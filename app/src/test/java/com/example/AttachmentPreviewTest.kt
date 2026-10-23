package com.example

import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.util.FileValidator
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentPreviewTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `attachment model classifies JPG JPEG PNG and WEBP as IMAGE`() {
        val jpgAttachment = Attachment(
            uri = "content://media/picker/0/media/101",
            fileName = "vacation.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1024L * 500
        )
        assertEquals(AttachmentType.IMAGE, jpgAttachment.type)
        assertTrue(jpgAttachment.isImage)

        val jpegAttachment = Attachment(
            uri = "content://media/picker/0/media/102",
            fileName = "camera.jpeg",
            mimeType = "image/jpeg",
            sizeBytes = 1024L * 600
        )
        assertEquals(AttachmentType.IMAGE, jpegAttachment.type)
        assertTrue(jpegAttachment.isImage)

        val pngAttachment = Attachment(
            uri = "content://media/picker/0/media/103",
            fileName = "diagram.png",
            mimeType = "image/png",
            sizeBytes = 1024L * 800
        )
        assertEquals(AttachmentType.IMAGE, pngAttachment.type)
        assertTrue(pngAttachment.isImage)

        val webpAttachment = Attachment(
            uri = "content://media/picker/0/media/104",
            fileName = "artwork.webp",
            mimeType = "image/webp",
            sizeBytes = 1024L * 300
        )
        assertEquals(AttachmentType.IMAGE, webpAttachment.type)
        assertTrue(webpAttachment.isImage)
    }

    @Test
    fun `attachment model classifies image without extension as IMAGE when MIME is image`() {
        // Android Photo Picker sometimes gives numeric display names without extension
        val pickerAttachment = Attachment(
            uri = "content://media/picker/0/media/1000000001",
            fileName = "1000000001",
            mimeType = "image/jpeg",
            sizeBytes = 1024L * 200
        )
        assertEquals(AttachmentType.IMAGE, pickerAttachment.type)
        assertTrue(pickerAttachment.isImage)
    }

    @Test
    fun `attachment model classifies PDF DOCX and TXT as DOCUMENT`() {
        val pdfAttachment = Attachment(
            uri = "content://com.android.providers.downloads/1",
            fileName = "whitepaper.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1024L * 1024
        )
        assertEquals(AttachmentType.DOCUMENT, pdfAttachment.type)
        assertFalse(pdfAttachment.isImage)

        val docxAttachment = Attachment(
            uri = "content://com.android.providers.downloads/2",
            fileName = "spec.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = 1024L * 512
        )
        assertEquals(AttachmentType.DOCUMENT, docxAttachment.type)
        assertFalse(docxAttachment.isImage)

        val txtAttachment = Attachment(
            uri = "content://com.android.providers.downloads/3",
            fileName = "notes.txt",
            mimeType = "text/plain",
            sizeBytes = 1024L * 10
        )
        assertEquals(AttachmentType.DOCUMENT, txtAttachment.type)
        assertFalse(txtAttachment.isImage)
    }

    @Test
    fun `MIME normalization resolves image-jpg to image-jpeg`() {
        val normalized = Attachment.normalizeMimeType("image/jpg", "picture.jpg")
        assertEquals("image/jpeg", normalized)

        val normalizedPjpeg = Attachment.normalizeMimeType("image/pjpeg", "picture.jpg")
        assertEquals("image/jpeg", normalizedPjpeg)
    }

    @Test
    fun `MIME normalization resolves missing MIME from file extension`() {
        assertEquals("image/jpeg", Attachment.normalizeMimeType(null, "photo.jpg"))
        assertEquals("image/jpeg", Attachment.normalizeMimeType("", "photo.jpeg"))
        assertEquals("image/png", Attachment.normalizeMimeType("application/octet-stream", "diagram.png"))
        assertEquals("image/webp", Attachment.normalizeMimeType(null, "banner.webp"))
        assertEquals("application/pdf", Attachment.normalizeMimeType(null, "doc.pdf"))
        assertEquals("text/plain", Attachment.normalizeMimeType(null, "notes.txt"))
    }

    @Test
    fun `viewModel stores and clears selected image attachment for composer preview`() = runTest(testDispatcher) {
        val repository = ChatRepositoryImpl(
            apiService = FakeMinovaApiService(),
            ioDispatcher = testDispatcher
        )
        val viewModel = MinovaViewModel(chatRepository = repository)

        val imageAttachment = Attachment(
            uri = "content://media/picker/0/com.android.providers.media.photopicker/media/1000000042",
            fileName = "sample_photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1024L * 450,
            type = AttachmentType.IMAGE
        )

        viewModel.selectAttachment(imageAttachment)

        val current = viewModel.selectedAttachment.value
        assertNotNull(current)
        assertEquals(AttachmentType.IMAGE, current?.type)
        assertTrue(current?.isImage == true)
        assertEquals("sample_photo.jpg", current?.fileName)
        assertEquals("content://media/picker/0/com.android.providers.media.photopicker/media/1000000042", current?.uri)

        // Clear attachment (e.g. user taps [X] in composer)
        viewModel.clearSelectedAttachment()
        assertNull(viewModel.selectedAttachment.value)
    }

    @Test
    fun `viewModel supports multiple attachments up to 5 and allows individual removal`() = runTest(testDispatcher) {
        val repository = ChatRepositoryImpl(
            apiService = FakeMinovaApiService(),
            ioDispatcher = testDispatcher
        )
        val viewModel = MinovaViewModel(chatRepository = repository)

        val att1 = Attachment(
            attachmentId = "att-1",
            uri = "content://media/1",
            fileName = "doc1.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1024L * 100,
            type = AttachmentType.PDF
        )
        val att2 = Attachment(
            attachmentId = "att-2",
            uri = "content://media/2",
            fileName = "image2.png",
            mimeType = "image/png",
            sizeBytes = 1024L * 200,
            type = AttachmentType.IMAGE
        )
        val att3 = Attachment(
            attachmentId = "att-3",
            uri = "content://media/3",
            fileName = "notes3.txt",
            mimeType = "text/plain",
            sizeBytes = 1024L * 50,
            type = AttachmentType.DOCUMENT
        )

        viewModel.addAttachments(listOf(att1, att2, att3))

        assertEquals(3, viewModel.selectedAttachments.value.size)
        assertEquals(att1, viewModel.selectedAttachment.value)

        // Remove the middle attachment
        viewModel.removeAttachment("att-2")
        assertEquals(2, viewModel.selectedAttachments.value.size)
        assertFalse(viewModel.selectedAttachments.value.any { it.attachmentId == "att-2" })
        assertTrue(viewModel.selectedAttachments.value.any { it.attachmentId == "att-1" })
        assertTrue(viewModel.selectedAttachments.value.any { it.attachmentId == "att-3" })

        // Clear all attachments
        viewModel.clearSelectedAttachments()
        assertTrue(viewModel.selectedAttachments.value.isEmpty())
        assertNull(viewModel.selectedAttachment.value)
    }

    @Test
    fun `Message allAttachments returns both attachment and attachments list without duplicates`() {
        val attA = Attachment(attachmentId = "a", uri = "content://a", fileName = "a.pdf", mimeType = "application/pdf", sizeBytes = 1024L)
        val attB = Attachment(attachmentId = "b", uri = "content://b", fileName = "b.png", mimeType = "image/png", sizeBytes = 2048L)
        val attC = Attachment(attachmentId = "c", uri = "content://c", fileName = "c.txt", mimeType = "text/plain", sizeBytes = 512L)

        val message = com.example.data.model.Message(
            id = "msg-1",
            conversationId = "conv-1",
            sender = com.example.data.model.MessageSender.USER,
            content = "Analyze these files",
            timestamp = "10:00 AM",
            attachment = attA,
            attachments = listOf(attA, attB, attC)
        )

        val all = message.allAttachments
        assertEquals(3, all.size)
        assertEquals(listOf(attA, attB, attC), all)
    }
}
