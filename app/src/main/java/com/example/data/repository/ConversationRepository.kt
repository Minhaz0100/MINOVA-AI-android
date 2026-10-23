package com.example.data.repository

import com.example.MinovaApplication
import com.example.data.local.dao.AttachmentDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.entity.AttachmentEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.model.CloudConversationRecord
import com.example.data.model.CloudMessageRecord
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.util.DateTimeFormatterHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

interface ConversationRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    fun getActiveConversations(): Flow<List<Conversation>>
    fun getArchivedConversations(): Flow<List<Conversation>>
    fun getPinnedConversations(): Flow<List<Conversation>>
    fun getMessagesForConversation(conversationId: String): Flow<List<Message>>
    suspend fun getMessagesSync(conversationId: String): List<Message>
    suspend fun getConversationById(id: String): Conversation?
    suspend fun createConversation(
        id: String,
        title: String,
        isPinned: Boolean = false,
        isArchived: Boolean = false,
        responseStyle: ResponseStyle? = null,
        responseLanguage: ResponseLanguage? = null,
        responseFormat: ResponseFormat? = null
    ): Conversation
    suspend fun updateConversationPreferences(
        id: String,
        style: ResponseStyle?,
        language: ResponseLanguage?,
        format: ResponseFormat?
    )
    suspend fun updateConversationTitle(id: String, title: String)
    suspend fun updateConversationLastMessage(id: String, lastMessage: String)
    suspend fun togglePin(id: String)
    suspend fun toggleArchive(id: String)
    suspend fun deleteConversation(id: String)
    suspend fun saveMessage(message: Message)
    suspend fun updateMessage(message: Message)
    suspend fun deleteMessage(id: String)
    suspend fun toggleMessagePin(id: String)
    fun searchConversations(query: String): Flow<List<Conversation>>

    suspend fun getAllCloudConversationRecords(userId: String = ""): List<CloudConversationRecord> = emptyList()
    suspend fun getCloudConversationRecordById(id: String, userId: String = ""): CloudConversationRecord? = null
    suspend fun getCloudMessageRecordsSync(conversationId: String): List<CloudMessageRecord> = emptyList()
    suspend fun upsertSyncedConversation(record: CloudConversationRecord) {}
    suspend fun upsertSyncedMessage(record: CloudMessageRecord) {}
}

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ConversationRepository {

    override fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversationsFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun getActiveConversations(): Flow<List<Conversation>> {
        return conversationDao.getActiveConversationsFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun getArchivedConversations(): Flow<List<Conversation>> {
        return conversationDao.getArchivedConversationsFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun getPinnedConversations(): Flow<List<Conversation>> {
        return conversationDao.getPinnedConversationsFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<Message>> {
        val messagesFlow = messageDao.getMessagesForConversationFlow(conversationId)
        val attachmentsFlow = attachmentDao?.getAttachmentsForConversationFlow(conversationId)

        return if (attachmentsFlow != null) {
            combine(messagesFlow, attachmentsFlow) { messages, attachments ->
                val attachmentMap = attachments.groupBy { it.messageId }
                messages.map { msgEntity ->
                    val attachEntities = attachmentMap[msgEntity.id] ?: emptyList()
                    msgEntity.toDomain(attachEntities)
                }
            }.flowOn(ioDispatcher)
        } else {
            messagesFlow
                .map { list -> list.map { it.toDomain(emptyList()) } }
                .flowOn(ioDispatcher)
        }
    }

    override suspend fun getMessagesSync(conversationId: String): List<Message> = withContext(ioDispatcher) {
        val messages = messageDao.getMessagesForConversation(conversationId)
        val attachments = attachmentDao?.getAttachmentsForConversation(conversationId) ?: emptyList()
        val attachmentMap = attachments.groupBy { it.messageId }
        messages.map { it.toDomain(attachmentMap[it.id] ?: emptyList()) }
    }

    override suspend fun getConversationById(id: String): Conversation? = withContext(ioDispatcher) {
        conversationDao.getConversationById(id)?.toDomain()
    }

    override suspend fun createConversation(
        id: String,
        title: String,
        isPinned: Boolean,
        isArchived: Boolean,
        responseStyle: ResponseStyle?,
        responseLanguage: ResponseLanguage?,
        responseFormat: ResponseFormat?
    ): Conversation = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            isPinned = isPinned,
            isArchived = isArchived,
            lastMessage = "",
            responseStyle = responseStyle?.name,
            responseLanguage = responseLanguage?.name,
            responseFormat = responseFormat?.name
        )
        conversationDao.insertOrUpdate(entity)
        entity.toDomain()
    }

    override suspend fun updateConversationPreferences(
        id: String,
        style: ResponseStyle?,
        language: ResponseLanguage?,
        format: ResponseFormat?
    ): Unit = withContext(ioDispatcher) {
        conversationDao.updateResponsePreferences(
            id = id,
            style = style?.name,
            language = language?.name,
            format = format?.name,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateConversationTitle(id: String, title: String): Unit = withContext(ioDispatcher) {
        conversationDao.updateTitle(id, title, System.currentTimeMillis())
    }

    override suspend fun updateConversationLastMessage(id: String, lastMessage: String): Unit = withContext(ioDispatcher) {
        conversationDao.updateLastMessage(id, lastMessage, System.currentTimeMillis())
    }

    override suspend fun togglePin(id: String): Unit = withContext(ioDispatcher) {
        val conv = conversationDao.getConversationById(id) ?: return@withContext
        conversationDao.setPinned(id, !conv.isPinned, System.currentTimeMillis())
    }

    override suspend fun toggleArchive(id: String): Unit = withContext(ioDispatcher) {
        val conv = conversationDao.getConversationById(id) ?: return@withContext
        conversationDao.setArchived(id, !conv.isArchived, System.currentTimeMillis())
    }

    override suspend fun deleteConversation(id: String): Unit = withContext(ioDispatcher) {
        attachmentDao?.deleteByConversationId(id)
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteById(id)
    }

    override suspend fun saveMessage(message: Message): Unit = withContext(ioDispatcher) {
        val conv = conversationDao.getConversationById(message.conversationId)
        val now = System.currentTimeMillis()
        if (conv == null) {
            val title = if (message.content.isNotBlank()) message.content.take(30) else "New Conversation"
            conversationDao.insertOrUpdate(
                ConversationEntity(
                    id = message.conversationId,
                    title = title,
                    createdAt = now,
                    updatedAt = now,
                    lastMessage = message.content
                )
            )
        } else if (message.content.isNotBlank()) {
            conversationDao.updateLastMessage(message.conversationId, message.content.take(60), now)
        }
        messageDao.insert(message.toEntity(now))

        val allAtts = message.allAttachments
        if (allAtts.isNotEmpty() && attachmentDao != null) {
            val entities = allAtts.map { att ->
                AttachmentEntity(
                    attachmentId = att.attachmentId,
                    messageId = message.id,
                    conversationId = message.conversationId,
                    fileName = att.fileName,
                    mimeType = att.mimeType,
                    sizeBytes = att.sizeBytes,
                    localUri = att.uri,
                    createdAt = now
                )
            }
            attachmentDao.insertAll(entities)
        }
    }

    override suspend fun updateMessage(message: Message): Unit = withContext(ioDispatcher) {
        val existing = messageDao.getMessageById(message.id)
        val timestamp = existing?.timestamp ?: System.currentTimeMillis()
        messageDao.update(message.toEntity(timestamp))
        if (message.content.isNotBlank()) {
            conversationDao.updateLastMessage(message.conversationId, message.content.take(60), System.currentTimeMillis())
        }
        val allAtts = message.allAttachments
        if (allAtts.isNotEmpty() && attachmentDao != null) {
            val entities = allAtts.map { att ->
                AttachmentEntity(
                    attachmentId = att.attachmentId,
                    messageId = message.id,
                    conversationId = message.conversationId,
                    fileName = att.fileName,
                    mimeType = att.mimeType,
                    sizeBytes = att.sizeBytes,
                    localUri = att.uri,
                    createdAt = timestamp
                )
            }
            attachmentDao.insertAll(entities)
        }
    }

    override suspend fun deleteMessage(id: String): Unit = withContext(ioDispatcher) {
        attachmentDao?.deleteByMessageId(id)
        messageDao.deleteById(id)
    }

    override suspend fun toggleMessagePin(id: String): Unit = withContext(ioDispatcher) {
        val existing = messageDao.getMessageById(id) ?: return@withContext
        messageDao.updatePinned(id, !existing.isPinned)
    }

    override fun searchConversations(query: String): Flow<List<Conversation>> {
        val cleanQuery = query.trim()
        return conversationDao.searchConversations(cleanQuery)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAllCloudConversationRecords(userId: String): List<CloudConversationRecord> =
        withContext(ioDispatcher) {
            conversationDao.getAllConversationsFlow().first().map { it.toCloudRecord(userId) }
        }

    override suspend fun getCloudConversationRecordById(
        id: String,
        userId: String
    ): CloudConversationRecord? = withContext(ioDispatcher) {
        conversationDao.getConversationById(id)?.toCloudRecord(userId)
    }

    override suspend fun getCloudMessageRecordsSync(conversationId: String): List<CloudMessageRecord> =
        withContext(ioDispatcher) {
            messageDao.getMessagesForConversation(conversationId).map { it.toCloudRecord() }
        }

    override suspend fun upsertSyncedConversation(record: CloudConversationRecord): Unit =
        withContext(ioDispatcher) {
            val entity = ConversationEntity(
                id = record.id,
                title = record.title,
                createdAt = record.createdAtMillis,
                updatedAt = record.updatedAtMillis,
                isPinned = record.isPinned,
                isArchived = record.isArchived,
                lastMessage = record.lastMessage,
                responseStyle = record.responseStyle,
                responseLanguage = record.responseLanguage,
                responseFormat = record.responseFormat
            )
            conversationDao.insertOrUpdate(entity)
        }

    override suspend fun upsertSyncedMessage(record: CloudMessageRecord): Unit =
        withContext(ioDispatcher) {
            val entity = MessageEntity(
                id = record.id,
                conversationId = record.conversationId,
                sender = if (record.sender.equals("USER", ignoreCase = true)) "USER" else "ASSISTANT",
                content = record.content,
                timestamp = record.timestampMillis,
                timestampFormatted = DateTimeFormatterHelper.formatTime(record.timestampMillis),
                status = record.status,
                errorMessage = record.errorMessage,
                attachmentType = record.attachmentType,
                attachmentName = record.attachmentName,
                codeSnippet = record.codeSnippet,
                codeLanguage = record.codeLanguage,
                isPinned = record.isPinned
            )
            messageDao.insert(entity)
        }

    private fun ConversationEntity.toCloudRecord(userId: String): CloudConversationRecord {
        return CloudConversationRecord(
            id = id,
            userId = userId,
            title = title,
            createdAtMillis = createdAt,
            updatedAtMillis = updatedAt,
            isPinned = isPinned,
            isArchived = isArchived,
            lastMessage = lastMessage,
            responseStyle = responseStyle,
            responseLanguage = responseLanguage,
            responseFormat = responseFormat
        )
    }

    private fun MessageEntity.toCloudRecord(): CloudMessageRecord {
        return CloudMessageRecord(
            id = id,
            conversationId = conversationId,
            sender = sender,
            content = content,
            timestampMillis = timestamp,
            status = status,
            errorMessage = errorMessage,
            attachmentType = attachmentType,
            attachmentName = attachmentName,
            codeSnippet = codeSnippet,
            codeLanguage = codeLanguage,
            isPinned = isPinned
        )
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            title = title,
            timestamp = DateTimeFormatterHelper.formatDisplayTimestamp(updatedAt),
            category = DateTimeFormatterHelper.determineCategory(updatedAt),
            isPinned = isPinned,
            isArchived = isArchived,
            lastMessage = lastMessage,
            responseStyle = responseStyle?.let { ResponseStyle.fromString(it) },
            responseLanguage = responseLanguage?.let { ResponseLanguage.fromString(it) },
            responseFormat = responseFormat?.let { ResponseFormat.fromString(it) }
        )
    }

    private fun Message.toEntity(timestampMillis: Long): MessageEntity {
        val firstAtt = allAttachments.firstOrNull()
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            sender = if (sender == MessageSender.USER) "USER" else "ASSISTANT",
            content = content,
            timestamp = timestampMillis,
            timestampFormatted = timestamp.ifEmpty { DateTimeFormatterHelper.formatTime(timestampMillis) },
            status = status.name,
            errorMessage = errorMessage,
            attachmentType = (firstAtt?.type ?: attachmentType)?.name,
            attachmentName = if (allAttachments.size > 1) "${allAttachments.size} files" else (firstAtt?.fileName ?: attachmentName),
            codeSnippet = codeSnippet,
            codeLanguage = codeLanguage,
            isPinned = isPinned
        )
    }

    private fun MessageEntity.toDomain(attachmentEntities: List<AttachmentEntity>): Message {
        val senderEnum = if (sender.equals("USER", ignoreCase = true)) MessageSender.USER else MessageSender.ASSISTANT
        val statusEnum = try {
            MessageStatus.valueOf(status)
        } catch (_: Exception) {
            MessageStatus.SENT
        }
        val attachEnum = attachmentType?.let {
            try {
                AttachmentType.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }

        val domainAttachments = attachmentEntities.map {
            Attachment(
                attachmentId = it.attachmentId,
                uri = it.localUri,
                fileName = it.fileName,
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes,
                type = Attachment.determineType(it.fileName, it.mimeType)
            )
        }
        val firstAtt = domainAttachments.firstOrNull()

        return Message(
            id = id,
            conversationId = conversationId,
            sender = senderEnum,
            content = content,
            timestamp = if (timestampFormatted.isNotBlank()) timestampFormatted else DateTimeFormatterHelper.formatTime(timestamp),
            status = statusEnum,
            errorMessage = errorMessage,
            codeSnippet = codeSnippet,
            codeLanguage = codeLanguage,
            attachmentType = firstAtt?.type ?: attachEnum,
            attachmentName = if (domainAttachments.size > 1) "${domainAttachments.size} files" else (firstAtt?.fileName ?: attachmentName),
            attachment = firstAtt,
            attachments = domainAttachments,
            isPinned = isPinned
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ConversationRepository? = null

        fun getInstance(): ConversationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    try {
                        val app = MinovaApplication.instance
                        ConversationRepositoryImpl(
                            conversationDao = app.database.conversationDao(),
                            messageDao = app.database.messageDao(),
                            attachmentDao = app.database.attachmentDao()
                        ).also { INSTANCE = it }
                    } catch (_: Throwable) {
                        InMemoryConversationRepository().also { INSTANCE = it }
                    }
                }
            }
        }
    }
}

/**
 * Lightweight In-Memory fallback implementation for standalone unit tests
 */
class InMemoryConversationRepository : ConversationRepository {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val convCreatedAt = mutableMapOf<String, Long>()
    private val convUpdatedAt = mutableMapOf<String, Long>()
    private val msgTimestampMillis = mutableMapOf<String, Long>()

    override fun getAllConversations(): Flow<List<Conversation>> = _conversations

    override fun getActiveConversations(): Flow<List<Conversation>> = _conversations.map { list ->
        list.filterNot { it.isArchived }
    }

    override fun getArchivedConversations(): Flow<List<Conversation>> = _conversations.map { list ->
        list.filter { it.isArchived }
    }

    override fun getPinnedConversations(): Flow<List<Conversation>> = _conversations.map { list ->
        list.filter { it.isPinned }
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<Message>> = _messages.map { map ->
        map[conversationId] ?: emptyList()
    }

    override suspend fun getMessagesSync(conversationId: String): List<Message> {
        return _messages.value[conversationId] ?: emptyList()
    }

    override suspend fun getConversationById(id: String): Conversation? {
        return _conversations.value.find { it.id == id }
    }

    override suspend fun createConversation(
        id: String,
        title: String,
        isPinned: Boolean,
        isArchived: Boolean,
        responseStyle: ResponseStyle?,
        responseLanguage: ResponseLanguage?,
        responseFormat: ResponseFormat?
    ): Conversation {
        val now = System.currentTimeMillis()
        convCreatedAt[id] = convCreatedAt[id] ?: now
        convUpdatedAt[id] = now
        val conv = Conversation(
            id = id,
            title = title,
            timestamp = DateTimeFormatterHelper.formatTime(now),
            category = "Today",
            isPinned = isPinned,
            isArchived = isArchived,
            lastMessage = "",
            responseStyle = responseStyle,
            responseLanguage = responseLanguage,
            responseFormat = responseFormat
        )
        _conversations.update { listOf(conv) + it.filterNot { c -> c.id == id } }
        return conv
    }

    override suspend fun updateConversationPreferences(
        id: String,
        style: ResponseStyle?,
        language: ResponseLanguage?,
        format: ResponseFormat?
    ) {
        convUpdatedAt[id] = System.currentTimeMillis()
        _conversations.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        responseStyle = style,
                        responseLanguage = language,
                        responseFormat = format
                    )
                } else it
            }
        }
    }

    override suspend fun updateConversationTitle(id: String, title: String) {
        convUpdatedAt[id] = System.currentTimeMillis()
        _conversations.update { list ->
            list.map { if (it.id == id) it.copy(title = title) else it }
        }
    }

    override suspend fun updateConversationLastMessage(id: String, lastMessage: String) {
        convUpdatedAt[id] = System.currentTimeMillis()
        _conversations.update { list ->
            list.map { if (it.id == id) it.copy(lastMessage = lastMessage) else it }
        }
    }

    override suspend fun togglePin(id: String) {
        convUpdatedAt[id] = System.currentTimeMillis()
        _conversations.update { list ->
            list.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it }
        }
    }

    override suspend fun toggleArchive(id: String) {
        convUpdatedAt[id] = System.currentTimeMillis()
        _conversations.update { list ->
            list.map { if (it.id == id) it.copy(isArchived = !it.isArchived) else it }
        }
    }

    override suspend fun deleteConversation(id: String) {
        convCreatedAt.remove(id)
        convUpdatedAt.remove(id)
        _conversations.update { it.filterNot { c -> c.id == id } }
        _messages.update { it - id }
    }

    override suspend fun saveMessage(message: Message) {
        val convId = message.conversationId
        val now = System.currentTimeMillis()
        msgTimestampMillis[message.id] = msgTimestampMillis[message.id] ?: now
        convUpdatedAt[convId] = now
        _messages.update { current ->
            val list = current[convId] ?: emptyList()
            val deduped = if (list.any { it.id == message.id }) {
                list.map { if (it.id == message.id) message else it }
            } else {
                list + message
            }
            current + (convId to deduped)
        }
        if (_conversations.value.none { it.id == convId }) {
            createConversation(convId, message.content.take(30).ifEmpty { "New Conversation" })
        } else if (message.content.isNotBlank()) {
            updateConversationLastMessage(convId, message.content.take(60))
        }
    }

    override suspend fun updateMessage(message: Message) {
        val convId = message.conversationId
        convUpdatedAt[convId] = System.currentTimeMillis()
        _messages.update { current ->
            val list = current[convId] ?: emptyList()
            val updated = list.map { if (it.id == message.id) message else it }
            current + (convId to updated)
        }
    }

    override suspend fun deleteMessage(id: String) {
        msgTimestampMillis.remove(id)
        _messages.update { current ->
            current.mapValues { (_, list) -> list.filterNot { it.id == id } }
        }
    }

    override suspend fun toggleMessagePin(id: String) {
        _messages.update { current ->
            current.mapValues { (_, list) ->
                list.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it }
            }
        }
    }

    override fun searchConversations(query: String): Flow<List<Conversation>> {
        return _conversations.map { list ->
            list.filter { it.title.contains(query, ignoreCase = true) || it.lastMessage.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun getAllCloudConversationRecords(userId: String): List<CloudConversationRecord> {
        return _conversations.value.map { conv ->
            conv.toCloudRecord(
                userId = userId,
                createdAtMillis = convCreatedAt[conv.id] ?: 1000L,
                updatedAtMillis = convUpdatedAt[conv.id] ?: 1000L
            )
        }
    }

    override suspend fun getCloudConversationRecordById(
        id: String,
        userId: String
    ): CloudConversationRecord? {
        val conv = _conversations.value.find { it.id == id } ?: return null
        return conv.toCloudRecord(
            userId = userId,
            createdAtMillis = convCreatedAt[conv.id] ?: 1000L,
            updatedAtMillis = convUpdatedAt[conv.id] ?: 1000L
        )
    }

    override suspend fun getCloudMessageRecordsSync(conversationId: String): List<CloudMessageRecord> {
        val list = _messages.value[conversationId] ?: emptyList()
        return list.mapIndexed { idx, msg ->
            CloudMessageRecord(
                id = msg.id,
                conversationId = msg.conversationId,
                sender = if (msg.sender == MessageSender.USER) "USER" else "ASSISTANT",
                content = msg.content,
                timestampMillis = msgTimestampMillis[msg.id] ?: (1000L + idx * 10L),
                status = msg.status.name,
                errorMessage = msg.errorMessage,
                attachmentType = msg.attachmentType?.name,
                attachmentName = msg.attachmentName,
                codeSnippet = msg.codeSnippet,
                codeLanguage = msg.codeLanguage,
                isPinned = msg.isPinned
            )
        }
    }

    override suspend fun upsertSyncedConversation(record: CloudConversationRecord) {
        convCreatedAt[record.id] = record.createdAtMillis
        convUpdatedAt[record.id] = record.updatedAtMillis
        val domain = Conversation(
            id = record.id,
            title = record.title,
            timestamp = DateTimeFormatterHelper.formatDisplayTimestamp(record.updatedAtMillis),
            category = DateTimeFormatterHelper.determineCategory(record.updatedAtMillis),
            isPinned = record.isPinned,
            isArchived = record.isArchived,
            lastMessage = record.lastMessage,
            responseStyle = record.responseStyle?.let { ResponseStyle.fromString(it) },
            responseLanguage = record.responseLanguage?.let { ResponseLanguage.fromString(it) },
            responseFormat = record.responseFormat?.let { ResponseFormat.fromString(it) }
        )
        _conversations.update { current ->
            val updated = listOf(domain) + current.filterNot { it.id == record.id }
            updated.sortedWith(
                compareByDescending<Conversation> { it.isPinned }
                    .thenByDescending { convUpdatedAt[it.id] ?: 0L }
            )
        }
    }

    override suspend fun upsertSyncedMessage(record: CloudMessageRecord) {
        msgTimestampMillis[record.id] = record.timestampMillis
        val senderEnum = if (record.sender.equals("USER", ignoreCase = true)) MessageSender.USER else MessageSender.ASSISTANT
        val statusEnum = try {
            MessageStatus.valueOf(record.status)
        } catch (_: Exception) {
            MessageStatus.SENT
        }
        val attachEnum = record.attachmentType?.let {
            try {
                AttachmentType.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }
        val domainMsg = Message(
            id = record.id,
            conversationId = record.conversationId,
            sender = senderEnum,
            content = record.content,
            timestamp = DateTimeFormatterHelper.formatTime(record.timestampMillis),
            status = statusEnum,
            errorMessage = record.errorMessage,
            codeSnippet = record.codeSnippet,
            codeLanguage = record.codeLanguage,
            attachmentType = attachEnum,
            attachmentName = record.attachmentName,
            isPinned = record.isPinned
        )
        _messages.update { current ->
            val list = current[record.conversationId] ?: emptyList()
            val deduped = (list.filterNot { it.id == record.id } + domainMsg)
                .sortedBy { msgTimestampMillis[it.id] ?: 0L }
            current + (record.conversationId to deduped)
        }
    }

    private fun Conversation.toCloudRecord(
        userId: String,
        createdAtMillis: Long,
        updatedAtMillis: Long
    ): CloudConversationRecord {
        return CloudConversationRecord(
            id = id,
            userId = userId,
            title = title,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            isPinned = isPinned,
            isArchived = isArchived,
            lastMessage = lastMessage,
            responseStyle = responseStyle?.name,
            responseLanguage = responseLanguage?.name,
            responseFormat = responseFormat?.name
        )
    }
}
