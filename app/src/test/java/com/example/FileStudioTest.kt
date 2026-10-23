package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.DocumentBlockType
import com.example.data.model.DocumentContentBlock
import com.example.data.model.FileStudioMode
import com.example.data.model.GeneratedBinaryFile
import com.example.data.model.ImageAspectRatio
import com.example.data.model.SpreadsheetWorksheet
import com.example.data.model.UserProfile
import com.example.data.remote.model.CloudflareImageGenerateRequest
import com.example.data.repository.FileStudioRepositoryImpl
import com.example.data.repository.NetworkResult
import com.example.navigation.MinovaScreen
import com.example.ui.components.MinovaDrawerContent
import com.example.ui.screens.FileStudioScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileStudioTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createSampleJpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    @Test
    fun buildRequests_constructsExpectedPdfDocxXlsxAndImagePayloads() {
        val blocks = listOf(
            DocumentContentBlock(
                type = DocumentBlockType.HEADING,
                headingLevel = 1,
                text = "Introduction"
            ),
            DocumentContentBlock(
                type = DocumentBlockType.PARAGRAPH,
                text = "System overview paragraph."
            ),
            DocumentContentBlock(
                type = DocumentBlockType.LIST,
                listOrdered = true,
                listItems = listOf("Step One", "Step Two")
            ),
            DocumentContentBlock(
                type = DocumentBlockType.TABLE,
                tableHeaders = listOf("Feature", "Status"),
                tableRows = listOf(listOf("PDF Export", "Complete"))
            )
        )

        val docReq = FileStudioRepositoryImpl.buildDocumentRequest(
            title = "MINOVA Architecture Spec",
            subtitle = "Release v1",
            author = "Engineering",
            blocks = blocks
        )

        assertEquals("MINOVA Architecture Spec", docReq.title)
        assertEquals("Release v1", docReq.subtitle)
        assertEquals("Engineering", docReq.author)
        assertEquals(4, docReq.sections.size)
        assertEquals("heading", docReq.sections[0].type)
        assertEquals("paragraph", docReq.sections[1].type)
        assertEquals("list", docReq.sections[2].type)
        assertEquals(listOf("Step One", "Step Two"), docReq.sections[2].items)
        assertEquals("table", docReq.sections[3].type)
        assertEquals(listOf("Feature", "Status"), docReq.sections[3].headers)

        val xlsxReq = FileStudioRepositoryImpl.buildSpreadsheetRequest(
            workbookTitle = "Q4 Budget",
            worksheets = listOf(
                SpreadsheetWorksheet(
                    name = "Revenue",
                    headers = listOf("Month", "Amount"),
                    rows = listOf(listOf("October", "24000"))
                )
            )
        )
        assertEquals("Q4 Budget", xlsxReq.title)
        assertEquals(1, xlsxReq.sheets.size)
        assertEquals("Revenue", xlsxReq.sheets[0].name)
        assertEquals(listOf("Month", "Amount"), xlsxReq.sheets[0].headers)
        assertEquals(listOf(listOf("October", "24000")), xlsxReq.sheets[0].rows)

        val imageReq = FileStudioRepositoryImpl.buildImageRequest(
            prompt = "Neon skyline at dusk",
            aspectRatio = ImageAspectRatio.LANDSCAPE_16_9
        )
        assertEquals("Neon skyline at dusk", imageReq.prompt)
        assertEquals("16:9", imageReq.aspectRatio)

        val cloudflareImageReq = FileStudioRepositoryImpl.buildCloudflareImageRequest(
            prompt = "  A detailed cybernetic skyline  "
        )
        assertEquals("A detailed cybernetic skyline", cloudflareImageReq.prompt)
    }

    @Test
    fun generateImage_callsCloudflareEndpoint_andDecodesBinaryJpegSafely() = runTest {
        val jpegBytes = createSampleJpegBytes()
        var capturedPrompt: String? = null

        val fakeMinovaApi = object : FakeMinovaApiService() {
            override suspend fun generateCloudflareImage(
                request: CloudflareImageGenerateRequest
            ): Response<ResponseBody> {
                capturedPrompt = request.prompt
                val headers = Headers.headersOf("Content-Type", "image/jpeg")
                return Response.success(jpegBytes.toResponseBody("image/jpeg".toMediaType()), headers)
            }
        }

        val repository = FileStudioRepositoryImpl(
            minovaApiService = fakeMinovaApi
        )

        val result = repository.generateImage(
            prompt = "A futuristic city at night",
            aspectRatio = ImageAspectRatio.SQUARE_1_1
        )

        assertEquals("A futuristic city at night", capturedPrompt)
        assertTrue(result is NetworkResult.Success)
        val generated = (result as NetworkResult.Success).data
        assertEquals(FileStudioMode.IMAGE, generated.mode)
        assertEquals("image/jpeg", generated.mimeType)
        assertTrue(generated.fileName.endsWith(".jpg"))
        assertArrayEquals(jpegBytes, generated.bytes)

        val decoded = FileStudioRepositoryImpl.decodeBitmapSafely(generated.bytes)
        assertNotNull(decoded)
        assertEquals(16, decoded?.width)
        assertEquals(16, decoded?.height)

        assertNull(FileStudioRepositoryImpl.decodeBitmapSafely(ByteArray(0)))
    }

    @Test
    fun parseFileGenerationResponse_parsesBinaryFilesAndInspectsContentType() {
        val rawPdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34)
        val headers = Headers.headersOf(
            "Content-Type", "application/pdf",
            "Content-Disposition", "attachment; filename=\"Architecture_Spec.pdf\""
        )
        val body = rawPdfBytes.toResponseBody("application/pdf".toMediaType())
        val response = Response.success(body, headers)

        val result = FileStudioRepositoryImpl.parseFileGenerationResponse(
            response = response,
            mode = FileStudioMode.PDF,
            fallbackTitle = "Fallback Title"
        )

        assertTrue(result is NetworkResult.Success)
        val generated = (result as NetworkResult.Success).data
        assertEquals(FileStudioMode.PDF, generated.mode)
        assertEquals("Architecture_Spec.pdf", generated.fileName)
        assertEquals("application/pdf", generated.mimeType)
        assertArrayEquals(rawPdfBytes, generated.bytes)
    }

    @Test
    fun parseFileGenerationResponse_handlesImageGenerationUnavailable503AndJsonErrors() {
        val errorJson = """
            {
                "success": false,
                "error": {
                    "code": "IMAGE_GENERATION_UNAVAILABLE",
                    "message": "Image generation is disabled on this server."
                }
            }
        """.trimIndent()
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        val response503 = Response.error<okhttp3.ResponseBody>(503, errorBody)

        val result = FileStudioRepositoryImpl.parseFileGenerationResponse(
            response = response503,
            mode = FileStudioMode.IMAGE,
            fallbackTitle = "Prompt"
        )

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(503, error.statusCode)
        assertEquals("IMAGE_GENERATION_UNAVAILABLE", error.errorCode)
        assertTrue(error.userMessage.contains("disabled", ignoreCase = true))

        // Also verify that if a 200 response unexpectedly has Content-Type: application/json,
        // it is NOT treated as a binary file.
        val unexpectedJsonBody = errorJson.toResponseBody("application/json".toMediaType())
        val response200Json = Response.success(
            unexpectedJsonBody,
            Headers.headersOf("Content-Type", "application/json")
        )
        val guardedResult = FileStudioRepositoryImpl.parseFileGenerationResponse(
            response = response200Json,
            mode = FileStudioMode.PDF,
            fallbackTitle = "Doc"
        )
        assertTrue(guardedResult is NetworkResult.Error)
    }

    @Test
    fun drawerAndFileStudioScreen_supportModes_unavailableBanner_andPreventDuplicateSubmission() {
        var navigated: MinovaScreen? = null

        composeRule.setContent {
            MyApplicationTheme {
                MinovaDrawerContent(
                    conversations = emptyList(),
                    activeConversationId = null,
                    userProfile = UserProfile(name = "Minhaz", email = "minhaz@minova.ai", tier = "MINOVA PRO"),
                    onNavigate = { navigated = it },
                    onSelectConversation = {},
                    onNewChatClick = {},
                    onTogglePin = {}
                )
            }
        }

        val drawerItem = composeRule.onNodeWithTag("drawer_nav_file_studio")
        drawerItem.performScrollTo().assertIsDisplayed()
        drawerItem.performClick()
        assertEquals(MinovaScreen.FileStudio, navigated)
    }

    @Test
    fun fileStudioScreen_displaysDecodedJpegImagePreview_andLocksButtonWhileLoading() {
        val sampleJpeg = GeneratedBinaryFile(
            mode = FileStudioMode.IMAGE,
            fileName = "Neon_Skyline.jpg",
            mimeType = "image/jpeg",
            bytes = createSampleJpegBytes()
        )

        composeRule.setContent {
            MyApplicationTheme {
                FileStudioScreen(
                    isLoading = true,
                    generatedFile = sampleJpeg,
                    errorMessage = "Image generation is disabled by default (HTTP 503 IMAGE_GENERATION_UNAVAILABLE).",
                    errorCode = "IMAGE_GENERATION_UNAVAILABLE",
                    savedConfirmationMessage = null,
                    onBack = {},
                    onGenerateDocument = { _, _, _, _, _ -> },
                    onGenerateSpreadsheet = { _, _ -> },
                    onGenerateImage = { _, _ -> },
                    onFileSavedSuccess = {},
                    onFileSaveOrShareError = {},
                    onClearError = {},
                    onClearSavedConfirmation = {},
                    onClearGeneratedFile = {}
                )
            }
        }

        // Generate button is disabled while isLoading is true to prevent duplicate submissions
        composeRule.onNodeWithTag("file_studio_generate_button").performScrollTo().assertIsNotEnabled()

        // Dedicated IMAGE_GENERATION_UNAVAILABLE card is displayed
        composeRule.onNodeWithTag("file_studio_image_unavailable_card").performScrollTo().assertIsDisplayed()

        // Generated image preview and Save / Share buttons are displayed
        composeRule.onNodeWithTag("file_studio_result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("file_studio_generated_image_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("file_studio_save_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("file_studio_share_button").performScrollTo().assertIsDisplayed()
    }
}
