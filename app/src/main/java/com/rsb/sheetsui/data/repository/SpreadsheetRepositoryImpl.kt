package com.rsb.sheetsui.data.repository

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rsb.sheetsui.data.local.dao.PendingActionDao
import com.rsb.sheetsui.data.local.dao.SheetCacheDao
import com.rsb.sheetsui.data.local.dao.SpreadsheetDao
import com.rsb.sheetsui.data.local.entity.CachedSpreadsheetEntity
import com.rsb.sheetsui.data.local.entity.SheetCacheEntity
import com.rsb.sheetsui.data.remote.api.GoogleDriveService
import com.rsb.sheetsui.data.remote.api.GoogleSheetService
import com.rsb.sheetsui.data.remote.dto.AddSheetParams
import com.rsb.sheetsui.data.remote.dto.AddSheetPropertiesDto
import com.rsb.sheetsui.data.remote.dto.AddSheetRequest
import com.rsb.sheetsui.data.remote.dto.AppendDimensionParams
import com.rsb.sheetsui.data.remote.dto.AppendDimensionRequest
import com.rsb.sheetsui.data.remote.dto.BatchUpdateSpreadsheetRequest
import com.rsb.sheetsui.data.remote.dto.DuplicateSheetParams
import com.rsb.sheetsui.data.remote.dto.DuplicateSheetRequest
import com.rsb.sheetsui.data.remote.dto.DeleteDimensionParams
import com.rsb.sheetsui.data.remote.dto.DeleteDimensionRequest
import com.rsb.sheetsui.data.remote.dto.DimensionRangeDto
import com.rsb.sheetsui.data.remote.dto.CellData
import com.rsb.sheetsui.data.remote.dto.CreateSpreadsheetRequestDto
import com.rsb.sheetsui.data.remote.dto.ExtendedValue
import com.rsb.sheetsui.data.remote.dto.CellFormat
import com.rsb.sheetsui.data.remote.dto.GridCoordinate
import com.rsb.sheetsui.data.remote.dto.RowData
import com.rsb.sheetsui.data.remote.dto.SpreadsheetPropertiesDto
import com.rsb.sheetsui.data.remote.dto.TextFormat
import com.rsb.sheetsui.data.remote.dto.GridPropertiesDto
import com.rsb.sheetsui.data.remote.dto.GridRange
import com.rsb.sheetsui.data.remote.dto.RepeatCellRequest
import com.rsb.sheetsui.data.remote.dto.RepeatCellParams
import com.rsb.sheetsui.data.remote.dto.SheetPropertiesUpdateDto
import com.rsb.sheetsui.data.remote.dto.UpdateCellsParams
import com.rsb.sheetsui.data.remote.dto.UpdateCellsRequest
import com.rsb.sheetsui.data.remote.dto.UpdateSheetPropertiesParams
import com.rsb.sheetsui.data.remote.dto.UpdateSheetPropertiesRequest
import com.rsb.sheetsui.data.remote.dto.NumberFormatDto
import com.rsb.sheetsui.data.remote.dto.ValueRangeDto
import com.rsb.sheetsui.data.remote.util.ApiErrorMapper
import com.rsb.sheetsui.domain.inference.HeaderDiscoveryEngine
import com.rsb.sheetsui.domain.model.ColumnValidation
import com.rsb.sheetsui.domain.util.ConnectivityMonitor
import com.rsb.sheetsui.domain.util.CurrencyLocale
import com.rsb.sheetsui.domain.model.Form
import com.rsb.sheetsui.domain.model.SheetData
import com.rsb.sheetsui.domain.model.SheetTab
import com.rsb.sheetsui.domain.model.Spreadsheet
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
import com.rsb.sheetsui.worker.SyncWorker
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SpreadsheetRepo"
private const val DEFAULT_HEADER = "Name"

private const val CACHE_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes

@Singleton
class SpreadsheetRepositoryImpl @Inject constructor(
    private val driveService: GoogleDriveService,
    private val sheetService: GoogleSheetService,
    private val dao: SpreadsheetDao,
    private val pendingActionDao: PendingActionDao,
    private val sheetCacheDao: SheetCacheDao,
    private val workManager: WorkManager,
    private val connectivityMonitor: ConnectivityMonitor
) : SpreadsheetRepository {

    private val gson = Gson()

    override fun getSpreadsheets(): Flow<Result<List<Spreadsheet>>> = flow {
        val cached = dao.getAllCached()
        if (cached.isNotEmpty()) {
            emit(Result.success(cached.map { it.toDomain() }))
        }

        try {
            val response = driveService.listFiles()
            if (response.isSuccessful) {
                val files = response.body()?.files.orEmpty()
                val spreadsheets = files.mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    Spreadsheet(
                        id = id,
                        name = dto.name ?: "Untitled",
                        modifiedTime = dto.modifiedTime,
                        createdTime = dto.createdTime
                    )
                }
                val entities = spreadsheets.map { it.toEntity() }
                dao.clearAll()
                dao.insertAll(entities)
                emit(Result.success(spreadsheets))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Drive API error ${response.code()}: $errorBody")
                if (cached.isEmpty()) {
                    emit(Result.failure(Exception("Drive API error ${response.code()}")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching spreadsheets", e)
            if (cached.isEmpty()) {
                emit(Result.failure(e))
            }
        }
    }

    override fun getForms(): Flow<Result<List<Form>>> = flow {
        try {
            val response = driveService.listForms()
            if (response.isSuccessful) {
                val files = response.body()?.files.orEmpty()
                val forms = files.mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    Form(
                        id = id,
                        name = dto.name ?: "Untitled form",
                        modifiedTime = dto.modifiedTime,
                        createdTime = dto.createdTime
                    )
                }
                emit(Result.success(forms))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Drive API listForms error ${response.code()}: $errorBody")
                emit(Result.failure(Exception("Failed to load forms (${response.code()})")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching forms", e)
            emit(Result.failure(e))
        }
    }

    override suspend fun getSheetTabs(spreadsheetId: String): Result<List<SheetTab>> = runCatching {
        val response = sheetService.getSpreadsheet(
            spreadsheetId = spreadsheetId,
            fields = "sheets(properties(sheetId,title,index))"
        )
        if (!response.isSuccessful) {
            throw Exception("Failed to load sheet tabs (${response.code()})")
        }
        val sheets = response.body()?.sheets.orEmpty()
        sheets.mapNotNull { sheet ->
            val props = sheet.properties ?: return@mapNotNull null
            val id = props.sheetId ?: return@mapNotNull null
            SheetTab(
                sheetId = id,
                title = props.title ?: "Sheet${id + 1}",
                index = props.index ?: 0
            )
        }
    }

    override suspend fun addSheet(spreadsheetId: String, title: String): Result<SheetTab> = runCatching {
        val request = BatchUpdateSpreadsheetRequest(
            requests = listOf(
                AddSheetRequest(
                    addSheet = AddSheetParams(
                        properties = AddSheetPropertiesDto(title = title)
                    )
                )
            ),
            includeSpreadsheetInResponse = true
        )
        val response = sheetService.batchUpdate(spreadsheetId, request)
        if (!response.isSuccessful) {
            val err = response.errorBody()?.string()
            Log.e(TAG, "addSheet failed ${response.code()}: $err")
            throw Exception("Failed to add sheet (${response.code()})")
        }
        val updated = response.body()?.updatedSpreadsheet
        val newSheet = updated?.sheets?.lastOrNull()?.properties ?: throw Exception("No new sheet in response")
        SheetTab(
            sheetId = newSheet.sheetId ?: throw Exception("No sheetId in response"),
            title = newSheet.title ?: title,
            index = newSheet.index ?: (updated.sheets?.size?.minus(1) ?: 0)
        )
    }

    override suspend fun renameSheet(spreadsheetId: String, sheetId: Int, newName: String): Result<Unit> = runCatching {
        val request = BatchUpdateSpreadsheetRequest(
            requests = listOf(
                UpdateSheetPropertiesRequest(
                    updateSheetProperties = UpdateSheetPropertiesParams(
                        properties = SheetPropertiesUpdateDto(sheetId = sheetId, title = newName),
                        fields = "title"
                    )
                )
            )
        )
        val response = sheetService.batchUpdate(spreadsheetId, request)
        if (!response.isSuccessful) {
            val err = response.errorBody()?.string()
            Log.e(TAG, "renameSheet failed ${response.code()}: $err")
            throw Exception("Failed to rename sheet (${response.code()})")
        }
    }

    override suspend fun duplicateSheet(spreadsheetId: String, sourceSheetId: Int, newSheetName: String): Result<SheetTab> = runCatching {
        val request = BatchUpdateSpreadsheetRequest(
            requests = listOf(
                DuplicateSheetRequest(
                    duplicateSheet = DuplicateSheetParams(
                        sourceSheetId = sourceSheetId,
                        newSheetName = newSheetName
                    )
                )
            )
        )
        val response = sheetService.batchUpdate(spreadsheetId, request)
        if (!response.isSuccessful) {
            val err = response.errorBody()?.string()
            Log.e(TAG, "duplicateSheet failed ${response.code()}: $err")
            throw Exception("Failed to duplicate sheet (${response.code()})")
        }
        val replies = response.body()?.replies
        val rawReply = replies?.firstOrNull() ?: throw Exception("No reply from duplicateSheet")
        val replyDto = gson.fromJson(gson.toJsonTree(rawReply), com.rsb.sheetsui.data.remote.dto.BatchUpdateReplyDto::class.java)
        val duplicateReply = replyDto.duplicateSheet ?: throw Exception("Invalid duplicateSheet reply")
        val props = duplicateReply.properties ?: throw Exception("No properties in duplicateSheet reply")
        SheetTab(
            sheetId = props.sheetId ?: throw Exception("No sheetId in reply"),
            title = props.title ?: newSheetName,
            index = props.index ?: 0
        )
    }

    override fun getSheetData(
        spreadsheetId: String,
        sheetName: String
    ): Flow<Result<SheetData>> = flow {
        try {
            val cacheKey = "$spreadsheetId:$sheetName"
            val cached = sheetCacheDao.getByKey(cacheKey)

            // Offline-first: if offline, serve from cache immediately
            if (!connectivityMonitor.isOnline) {
                val restored = cached?.let { restoreFromCache(it) }
                if (restored != null) {
                    Log.d(TAG, "Offline: serving cached sheet data for $cacheKey")
                    emit(Result.success(restored))
                    return@flow
                }
                emit(Result.failure(Exception("You're offline. No cached data available.")))
                return@flow
            }

            val driveModifiedResult = runCatching {
                driveService.getFile(spreadsheetId).body()?.modifiedTime
            }.getOrNull()

            if (cached != null && cached.lastModifiedTime == driveModifiedResult &&
                System.currentTimeMillis() - cached.fetchedAt < CACHE_MAX_AGE_MS
            ) {
                val restored = restoreFromCache(cached)
                if (restored != null) {
                    Log.d(TAG, "Using cached sheet data for $cacheKey")
                    emit(Result.success(restored))
                    return@flow
                }
            }

            val range = if (sheetName.isNotEmpty()) "$sheetName!A1:ZZ1000" else "A1:ZZ1000"
            val valuesResponse = sheetService.getValues(
                spreadsheetId = spreadsheetId,
                range = range,
                valueRenderOption = "FORMATTED_VALUE"
            )
            val formulaResponse = sheetService.getValues(
                spreadsheetId = spreadsheetId,
                range = range,
                valueRenderOption = "FORMULA"
            )
            val metadataResponse = sheetService.getSpreadsheet(
                spreadsheetId = spreadsheetId,
                fields = "sheets(properties,merges,filterViews,basicFilter,tables,developerMetadata)"
            )
            val gridResponse = sheetService.getSpreadsheet(
                spreadsheetId = spreadsheetId,
                includeGridData = true,
                fields = "sheets(data(rowData(values(dataValidation))))",
                ranges = listOf(if (sheetName.isNotEmpty()) "$sheetName!A1:ZZ10" else "A1:ZZ10")
            )

            if (!valuesResponse.isSuccessful) {
                val code = valuesResponse.code()
                val errBody = valuesResponse.errorBody()?.string()
                val apiError = when (code) {
                    429 -> com.rsb.sheetsui.domain.error.ApiError.RateLimit()
                    403 -> com.rsb.sheetsui.domain.error.ApiError.PermissionDenied()
                    else -> com.rsb.sheetsui.domain.error.ApiError.Generic(
                        userMessage = ApiErrorMapper.parseErrorMessage(errBody) ?: "Request failed ($code)"
                    )
                }
                emit(Result.failure(Exception(apiError.userMessage)))
                return@flow
            }

            val allRows = valuesResponse.body()?.values.orEmpty()
            val formulaRows = formulaResponse.body()?.values?.map { row ->
                row.map { cell ->
                    val s = cell?.toString()?.trim().orEmpty()
                    s.takeIf { s.startsWith("=") }
                }
            }.orEmpty()
            val targetSheet = getTargetSheet(metadataResponse, sheetName)
            val mergeRanges = parseMergeRanges(metadataResponse, sheetName)
            val isStructuredTable = (targetSheet?.filterViews?.isNotEmpty() == true ||
                targetSheet?.basicFilter != null || targetSheet?.tables?.isNotEmpty() == true)
            val columnValidations = parseColumnValidations(gridResponse, sheetName)
            val driveModified = driveModifiedResult ?: cached?.lastModifiedTime

            val discovery = HeaderDiscoveryEngine.discover(allRows, mergeRanges)
            val dataFormulaRows = formulaRows.drop(discovery.headerRowIndex + 1)

            val sheetData = SheetData(
                spreadsheetId = spreadsheetId,
                sheetName = sheetName,
                headers = discovery.headers,
                rows = discovery.dataRows,
                headerRowIndex = discovery.headerRowIndex,
                separatorRowIndices = discovery.separatorRowIndicesInData,
                mergeRanges = mergeRanges,
                formulaRows = dataFormulaRows,
                columnValidations = columnValidations,
                lastModifiedTime = driveModified,
                isStructuredTable = isStructuredTable
            )

            val dataHash = hashSheetData(discovery.headers, discovery.dataRows, dataFormulaRows)
            sheetCacheDao.deleteOlderThan(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            sheetCacheDao.insert(SheetCacheEntity(
                cacheKey = cacheKey,
                dataHash = dataHash,
                headersJson = gson.toJson(discovery.headers),
                rowsJson = gson.toJson(discovery.dataRows.map { it.map { v -> v?.toString() } }),
                formulaRowsJson = gson.toJson(dataFormulaRows),
                mergeRangesJson = gson.toJson(mergeRanges.map { listOf(it.startRowIndex, it.endRowIndex, it.startColumnIndex, it.endColumnIndex) }),
                columnValidationsJson = gson.toJson(columnValidations.map { (k, v) -> k.toString() to when (v) { is ColumnValidation.Dropdown -> "D:${v.options.joinToString("|")}"; is ColumnValidation.Checkbox -> "C" } }.toMap()),
                lastModifiedTime = driveModified,
                isStructuredTable = isStructuredTable,
                fetchedAt = System.currentTimeMillis(),
                headerRowIndex = discovery.headerRowIndex,
                separatorRowIndicesJson = gson.toJson(discovery.separatorRowIndicesInData.toList())
            ))

            emit(Result.success(sheetData))
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching sheet data", e)
            // Offline resilience: fall back to cache on network failure
            val cacheKey = "$spreadsheetId:$sheetName"
            val cached = sheetCacheDao.getByKey(cacheKey)
            val restored = cached?.let { restoreFromCache(it) }
            if (restored != null) {
                Log.d(TAG, "Network failed: serving cached sheet data for $cacheKey")
                emit(Result.success(restored))
            } else {
                val apiError = ApiErrorMapper.fromThrowable(e)
                emit(Result.failure(Exception(apiError.userMessage)))
            }
        }
    }

    private fun getTargetSheet(response: retrofit2.Response<com.rsb.sheetsui.data.remote.dto.SpreadsheetDto>, sheetName: String) =
        response.body()?.sheets?.let { sheets ->
            if (sheetName.isBlank()) sheets.firstOrNull()
            else sheets.firstOrNull { it.properties?.title?.equals(sheetName, ignoreCase = true) == true } ?: sheets.firstOrNull()
        }

    private fun parseColumnValidations(response: retrofit2.Response<com.rsb.sheetsui.data.remote.dto.SpreadsheetDto>, sheetName: String): Map<Int, ColumnValidation> {
        if (!response.isSuccessful) return emptyMap()
        val sheet = getTargetSheet(response, sheetName) ?: return emptyMap()
        val gridData = sheet.data?.firstOrNull() ?: return emptyMap()
        val found = mutableMapOf<Int, ColumnValidation>()
        gridData.rowData?.forEachIndexed { rowIdx, rowData ->
            rowData.values?.forEachIndexed { colIdx, cell ->
                if (found.containsKey(colIdx)) return@forEachIndexed
                val rule = cell.dataValidation?.condition ?: return@forEachIndexed
                when (rule.type?.uppercase()) {
                    "ONE_OF_LIST" -> rule.values?.mapNotNull { it.userEnteredValue }?.let { opts ->
                        if (opts.isNotEmpty()) found[colIdx] = ColumnValidation.Dropdown(opts)
                    }
                    "BOOLEAN" -> found[colIdx] = ColumnValidation.Checkbox
                    else -> {}
                }
            }
        }
        return found
    }

    private fun hashSheetData(headers: List<String>, rows: List<List<Any?>>, formulaRows: List<List<String?>>): String {
        val sb = StringBuilder()
        headers.forEach { sb.append(it) }
        rows.forEach { r -> r.forEach { sb.append(it?.toString().orEmpty()) } }
        formulaRows.forEach { r -> r.forEach { sb.append(it.orEmpty()) } }
        return sb.toString().hashCode().toString(16)
    }

    private fun restoreFromCache(cached: SheetCacheEntity): SheetData? = try {
        val headers = gson.fromJson<List<String>>(cached.headersJson, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
        val rows = gson.fromJson<List<List<String?>>>(cached.rowsJson, object : com.google.gson.reflect.TypeToken<List<List<String?>>>() {}.type)
            .map { r -> r.map { it as Any? } }
        val formulaRows = gson.fromJson<List<List<String?>>>(cached.formulaRowsJson, object : com.google.gson.reflect.TypeToken<List<List<String?>>>() {}.type)
        val mergeRangesList = gson.fromJson<List<List<Int>>>(cached.mergeRangesJson, object : com.google.gson.reflect.TypeToken<List<List<Int>>>() {}.type)
        val mergeRanges = mergeRangesList.map { l -> com.rsb.sheetsui.domain.util.MergeRange(l[0], l[1], l[2], l[3]) }
        val (spreadsheetId, sheetName) = cached.cacheKey.split(":", limit = 2)
        val validationsMap = gson.fromJson<Map<String, String>>(cached.columnValidationsJson, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
            ?: emptyMap()
        val columnValidations = validationsMap.mapNotNull { (k, v) ->
            k.toIntOrNull()?.let { col ->
                when {
                    v.startsWith("D:") -> col to ColumnValidation.Dropdown(v.removePrefix("D:").split("|").filter { it.isNotEmpty() })
                    v == "C" -> col to ColumnValidation.Checkbox
                    else -> null
                }
            }
        }.toMap()
        val separatorIndices = try {
            gson.fromJson<List<Int>>(cached.separatorRowIndicesJson, object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type).toSet()
        } catch (_: Exception) { emptySet<Int>() }
        SheetData(
            spreadsheetId = spreadsheetId,
            sheetName = sheetName,
            headers = headers,
            rows = rows,
            headerRowIndex = cached.headerRowIndex,
            separatorRowIndices = separatorIndices,
            mergeRanges = mergeRanges,
            formulaRows = formulaRows,
            columnValidations = columnValidations,
            lastModifiedTime = cached.lastModifiedTime,
            isStructuredTable = cached.isStructuredTable
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to restore from cache", e)
        null
    }

    private fun parseMergeRanges(metadataResponse: retrofit2.Response<com.rsb.sheetsui.data.remote.dto.SpreadsheetDto>, sheetName: String): List<com.rsb.sheetsui.domain.util.MergeRange> {
        if (!metadataResponse.isSuccessful) return emptyList()
        val sheets = metadataResponse.body()?.sheets.orEmpty()
        val targetSheet = if (sheetName.isBlank()) {
            sheets.firstOrNull()
        } else {
            sheets.firstOrNull { it.properties?.title?.equals(sheetName, ignoreCase = true) == true }
                ?: sheets.firstOrNull()
        }
        return (targetSheet?.merges.orEmpty()).map { m ->
            com.rsb.sheetsui.domain.util.MergeRange(
                startRowIndex = m.startRowIndex,
                endRowIndex = m.endRowIndex,
                startColumnIndex = m.startColumnIndex,
                endColumnIndex = m.endColumnIndex
            )
        }
    }

    // ── Create: File → Metadata → Headers ───────────────────────────────────

    override suspend fun createSpreadsheet(title: String): Result<Spreadsheet> = runCatching {
        // 1. Create file
        val createBody = CreateSpreadsheetRequestDto(
            properties = SpreadsheetPropertiesDto(title = title)
        )
        val createResp = sheetService.createSpreadsheet(createBody)
        if (!createResp.isSuccessful) {
            val msg = when (createResp.code()) {
                401 -> "Authorization failed. Please sign out and sign in again to grant permissions."
                else -> "Create spreadsheet failed (${createResp.code()})"
            }
            throw Exception(msg)
        }
        val dto = createResp.body() ?: throw Exception("Empty response body")
        val spreadsheetId = dto.spreadsheetId ?: throw Exception("No spreadsheetId in response")

        val sheetId = resolveSheetId(spreadsheetId, "")

        // batchUpdate: write header "Name" in A1 + bold formatting for row 0
        val headerCell = CellData(
            userEnteredValue = ExtendedValue(stringValue = DEFAULT_HEADER),
            userEnteredFormat = CellFormat(
                textFormat = TextFormat(bold = true)
            )
        )
        val updateCellsReq = UpdateCellsRequest(
            updateCells = UpdateCellsParams(
                rows = listOf(RowData(values = listOf(headerCell))),
                start = GridCoordinate(sheetId = sheetId, rowIndex = 0, columnIndex = 0),
                fields = "userEnteredValue,userEnteredFormat.textFormat.bold"
            )
        )
        val batchBody = BatchUpdateSpreadsheetRequest(requests = listOf(updateCellsReq))
        val batchResp = sheetService.batchUpdate(spreadsheetId, batchBody)
        if (!batchResp.isSuccessful) {
            val err = batchResp.errorBody()?.string()
            Log.e(TAG, "batchUpdate headers failed ${batchResp.code()}: $err")
            throw Exception("Failed to set initial headers (${batchResp.code()})")
        }

        Spreadsheet(id = spreadsheetId, name = dto.properties?.title ?: title)
    }

    override suspend fun appendRow(
        spreadsheetId: String,
        sheetName: String,
        row: List<Any?>,
        auditInfo: Pair<String, String>?
    ): Result<Unit> = runCatching {
        val rowWithAudit = if (auditInfo != null) {
            row + "${auditInfo.first} | ${auditInfo.second}"
        } else row
        try {
            val prefix = if (sheetName.isNotEmpty()) "$sheetName!" else ""
            val range = "${prefix}A1"
            val body = ValueRangeDto(
                majorDimension = "ROWS",
                values = listOf(rowWithAudit.map { it ?: "" })
            )

            val response = sheetService.appendValues(
                spreadsheetId = spreadsheetId,
                range = range,
                body = body,
                valueInputOption = "USER_ENTERED"
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Append API error ${response.code()}: $errorBody")
                if (isNetworkError(response.code())) savePendingAppend(spreadsheetId, sheetName, rowWithAudit)
                throw Exception("Failed to append row (${response.code()})")
            }
        } catch (e: Exception) {
            if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.io.IOException) {
                savePendingAppend(spreadsheetId, sheetName, rowWithAudit)
            }
            throw e
        }
    }

    private fun isNetworkError(code: Int): Boolean = code in 500..599 || code == 408

    private suspend fun savePendingAppend(spreadsheetId: String, sheetName: String, row: List<Any?>) {
        val json = gson.toJson(row.map { it?.toString() })
        pendingActionDao.insert(
            com.rsb.sheetsui.data.local.entity.PendingActionEntity(
                actionType = "APPEND",
                spreadsheetId = spreadsheetId,
                sheetName = sheetName,
                rowIndex = null,
                rowData = json,
                createdAt = System.currentTimeMillis()
            )
        )
        enqueueSyncWorker()
    }

    private suspend fun savePendingUpdate(spreadsheetId: String, sheetName: String, rowIndex: Int, row: List<Any?>) {
        val json = gson.toJson(row.map { it?.toString() })
        pendingActionDao.insert(
            com.rsb.sheetsui.data.local.entity.PendingActionEntity(
                actionType = "UPDATE",
                spreadsheetId = spreadsheetId,
                sheetName = sheetName,
                rowIndex = rowIndex,
                rowData = json,
                createdAt = System.currentTimeMillis()
            )
        )
        enqueueSyncWorker()
    }

    private fun enqueueSyncWorker() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork("sheets_sync", ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun updateRow(
        spreadsheetId: String,
        sheetName: String,
        rowIndex: Int,
        row: List<Any?>,
        mergeRanges: List<com.rsb.sheetsui.domain.util.MergeRange>?,
        headerRowIndex: Int,
        auditInfo: Pair<String, String>?
    ): Result<Unit> = runCatching {
        val rowWithAudit = if (auditInfo != null) {
            row + "${auditInfo.first} | ${auditInfo.second}"
        } else row
        try {
            val sheetId = resolveSheetId(spreadsheetId, sheetName)
            val sheetRowIndex = rowIndex + headerRowIndex + 1
            val requests = mutableListOf<Any>()

            mergeRanges?.let { merges ->
                row.indices.forEach { col ->
                    if (com.rsb.sheetsui.domain.util.MergedCellResolver.isMergedCell(merges, sheetRowIndex, col)) {
                        val merge = com.rsb.sheetsui.domain.util.MergedCellResolver.getMergeContaining(merges, sheetRowIndex, col)
                            ?: return@forEach
                        val cellData = CellData(
                            userEnteredValue = ExtendedValue(stringValue = row.getOrElse(col) { "" }?.toString().orEmpty())
                        )
                        requests.add(
                            RepeatCellRequest(
                                repeatCell = RepeatCellParams(
                                    range = GridRange(
                                        sheetId = sheetId,
                                        startRowIndex = merge.startRowIndex,
                                        endRowIndex = merge.endRowIndex,
                                        startColumnIndex = merge.startColumnIndex,
                                        endColumnIndex = merge.endColumnIndex
                                    ),
                                    cell = cellData,
                                    fields = "userEnteredValue"
                                )
                            )
                        )
                    }
                }
            }

            val sheetRow = rowIndex + headerRowIndex + 2
            val lastCol = columnLetter(rowWithAudit.size - 1)
            val prefix = if (sheetName.isNotEmpty()) "$sheetName!" else ""
            val range = "${prefix}A$sheetRow:$lastCol$sheetRow"
            val body = ValueRangeDto(
                range = range,
                majorDimension = "ROWS",
                values = listOf(rowWithAudit.map { it ?: "" })
            )
            val response = sheetService.updateValues(
                spreadsheetId = spreadsheetId,
                range = range,
                body = body,
                valueInputOption = "USER_ENTERED"
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Update API error ${response.code()}: $errorBody")
                if (isNetworkError(response.code())) savePendingUpdate(spreadsheetId, sheetName, rowIndex, rowWithAudit)
                throw Exception("Failed to update row (${response.code()})")
            }

            if (requests.isNotEmpty()) {
                val batchBody = BatchUpdateSpreadsheetRequest(requests = requests)
                val batchResp = sheetService.batchUpdate(spreadsheetId, batchBody)
                if (!batchResp.isSuccessful) {
                    val err = batchResp.errorBody()?.string()
                    Log.e(TAG, "batchUpdate RepeatCell failed ${batchResp.code()}: $err")
                    throw Exception("Failed to sync merged cells (${batchResp.code()})")
                }
            }
        } catch (e: Exception) {
            if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.io.IOException) {
                savePendingUpdate(spreadsheetId, sheetName, rowIndex, rowWithAudit)
            }
            throw e
        }
    }

    override suspend fun deleteRow(
        spreadsheetId: String,
        sheetName: String,
        rowIndex: Int,
        headerRowIndex: Int
    ): Result<Unit> = runCatching {
        val sheetId = resolveSheetId(spreadsheetId, sheetName)
        deleteRow(spreadsheetId, sheetId, rowIndex, headerRowIndex).getOrThrow()
    }

    override suspend fun deleteRow(
        spreadsheetId: String,
        sheetId: Int,
        rowIndex: Int,
        headerRowIndex: Int
    ): Result<Unit> = runCatching {
        val apiRowIndex = rowIndex + headerRowIndex + 1
        val deleteReq = DeleteDimensionRequest(
            deleteDimension = DeleteDimensionParams(
                range = DimensionRangeDto(
                    sheetId = sheetId,
                    dimension = "ROWS",
                    startIndex = apiRowIndex,
                    endIndex = apiRowIndex + 1
                )
            )
        )
        val batchBody = BatchUpdateSpreadsheetRequest(requests = listOf(deleteReq))
        val batchResp = sheetService.batchUpdate(spreadsheetId, batchBody)
        if (!batchResp.isSuccessful) {
            val err = batchResp.errorBody()?.string()
            Log.e(TAG, "deleteRow batchUpdate failed ${batchResp.code()}: $err")
            throw Exception("Failed to delete row (${batchResp.code()})")
        }
    }

    // ── Add Column: Metadata → AppendDimension + UpdateCells ─────────────────

    override suspend fun addColumn(
        spreadsheetId: String,
        sheetName: String,
        columnName: String,
        currentColumnCount: Int,
        headerRowIndex: Int
    ): Result<Unit> = runCatching {
        val sheetId = resolveSheetId(spreadsheetId, sheetName)

        val appendReq = AppendDimensionRequest(
            appendDimension = AppendDimensionParams(
                sheetId = sheetId,
                dimension = "COLUMNS",
                length = 1
            )
        )
        val headerCell = CellData(
            userEnteredValue = ExtendedValue(stringValue = columnName)
        )
        val updateReq = UpdateCellsRequest(
            updateCells = UpdateCellsParams(
                rows = listOf(RowData(values = listOf(headerCell))),
                start = GridCoordinate(
                    sheetId = sheetId,
                    rowIndex = headerRowIndex,
                    columnIndex = currentColumnCount
                ),
                fields = "userEnteredValue"
            )
        )
        val batchBody = BatchUpdateSpreadsheetRequest(
            requests = listOf(appendReq, updateReq)
        )
        val batchResp = sheetService.batchUpdate(spreadsheetId, batchBody)
        if (!batchResp.isSuccessful) {
            val err = batchResp.errorBody()?.string()
            Log.e(TAG, "batchUpdate addColumn failed ${batchResp.code()}: $err")
            throw Exception("Failed to add column (${batchResp.code()})")
        }
    }

    override suspend fun renameColumn(
        spreadsheetId: String,
        sheetName: String,
        columnIndex: Int,
        newName: String,
        headerRowIndex: Int
    ): Result<Unit> = runCatching {
        val prefix = if (sheetName.isNotEmpty()) "$sheetName!" else ""
        val colLetter = columnLetter(columnIndex)
        val a1Row = headerRowIndex + 1
        val range = "${prefix}${colLetter}$a1Row"

        val body = ValueRangeDto(
            majorDimension = "ROWS",
            values = listOf(listOf(newName))
        )
        val response = sheetService.updateValues(
            spreadsheetId = spreadsheetId,
            range = range,
            body = body,
            valueInputOption = "USER_ENTERED"
        )
        if (!response.isSuccessful) {
            val err = response.errorBody()?.string()
            Log.e(TAG, "renameColumn failed ${response.code()}: $err")
            throw Exception("Failed to rename column (${response.code()})")
        }
    }

    override suspend fun deleteColumn(
        spreadsheetId: String,
        sheetName: String,
        columnIndex: Int
    ): Result<Unit> = runCatching {
        val sheetId = resolveSheetId(spreadsheetId, sheetName)

        val deleteReq = DeleteDimensionRequest(
            deleteDimension = DeleteDimensionParams(
                range = DimensionRangeDto(
                    sheetId = sheetId,
                    dimension = "COLUMNS",
                    startIndex = columnIndex,
                    endIndex = columnIndex + 1
                )
            )
        )
        val batchBody = BatchUpdateSpreadsheetRequest(requests = listOf(deleteReq))
        val batchResp = sheetService.batchUpdate(spreadsheetId, batchBody)
        if (!batchResp.isSuccessful) {
            val err = batchResp.errorBody()?.string()
            Log.e(TAG, "deleteColumn batchUpdate failed ${batchResp.code()}: $err")
            throw Exception("Failed to delete column (${batchResp.code()})")
        }
    }

    // ── Initialize Headers: values.update → batchUpdate (bold + freeze) ─────

    override suspend fun initializeHeaders(
        spreadsheetId: String,
        sheetName: String,
        headers: List<String>,
        headerRowIndex: Int
    ): Result<Unit> = runCatching {
        if (headers.isEmpty()) throw Exception("Headers list cannot be empty")

        val sheetId = resolveSheetId(spreadsheetId, sheetName)

        val prefix = if (sheetName.isNotEmpty()) "$sheetName!" else ""
        val lastCol = columnLetter(headers.size - 1)
        val a1Row = headerRowIndex + 1
        val range = "${prefix}A$a1Row:${lastCol}$a1Row"

        // 2. values.update: write headers to A1:Z1
        val updateBody = ValueRangeDto(
            majorDimension = "ROWS",
            values = listOf(headers)
        )
        val updateResp = sheetService.updateValues(
            spreadsheetId = spreadsheetId,
            range = range,
            body = updateBody,
            valueInputOption = "USER_ENTERED"
        )
        if (!updateResp.isSuccessful) {
            val err = updateResp.errorBody()?.string()
            Log.e(TAG, "initializeHeaders values.update failed ${updateResp.code()}: $err")
            throw Exception("Failed to write headers (${updateResp.code()})")
        }

        // 3. batchUpdate: bold row 0 + freeze row 1
        val boldCell = CellData(
            userEnteredFormat = CellFormat(textFormat = TextFormat(bold = true))
        )
        val repeatReq = RepeatCellRequest(
            repeatCell = RepeatCellParams(
                range = GridRange(
                    sheetId = sheetId,
                    startRowIndex = headerRowIndex,
                    endRowIndex = headerRowIndex + 1,
                    startColumnIndex = 0,
                    endColumnIndex = headers.size
                ),
                cell = boldCell,
                fields = "userEnteredFormat.textFormat.bold"
            )
        )
        val freezeReq = UpdateSheetPropertiesRequest(
            updateSheetProperties = UpdateSheetPropertiesParams(
                properties = SheetPropertiesUpdateDto(
                    sheetId = sheetId,
                    gridProperties = GridPropertiesDto(frozenRowCount = headerRowIndex + 1)
                ),
                fields = "gridProperties.frozenRowCount"
            )
        )
        val requests = mutableListOf<Any>(repeatReq, freezeReq)

        // Apply currency format to Price/Total columns
        val currencyColumns = headers.mapIndexed { i, h ->
            val lower = h.lowercase().trim()
            i to (lower.contains("price") || lower.contains("total") || lower.contains("amount") || lower.contains("cost"))
        }.filter { it.second }.map { it.first }
        if (currencyColumns.isNotEmpty()) {
            val currencyFormat = CellFormat(numberFormat = NumberFormatDto(type = "CURRENCY", pattern = CurrencyLocale.defaultFormatPattern))
            currencyColumns.forEach { col ->
                val repeatCurrency = RepeatCellRequest(
                    repeatCell = RepeatCellParams(
                        range = GridRange(
                            sheetId = sheetId,
                            startRowIndex = headerRowIndex + 1,
                            endRowIndex = 1000,
                            startColumnIndex = col,
                            endColumnIndex = col + 1
                        ),
                        cell = CellData(userEnteredFormat = currencyFormat),
                        fields = "userEnteredFormat.numberFormat"
                    )
                )
                requests.add(repeatCurrency)
            }
        }

        val batchBody = BatchUpdateSpreadsheetRequest(requests = requests)
        val batchResp = sheetService.batchUpdate(spreadsheetId, batchBody)
        if (!batchResp.isSuccessful) {
            val err = batchResp.errorBody()?.string()
            Log.e(TAG, "initializeHeaders batchUpdate failed ${batchResp.code()}: $err")
            throw Exception("Failed to format headers (${batchResp.code()})")
        }
    }

    override suspend fun deleteSpreadsheet(fileId: String): Result<Unit> = runCatching {
        val response = driveService.deleteFile(fileId)
        if (!response.isSuccessful) {
            val err = response.errorBody()?.string()
            Log.e(TAG, "deleteSpreadsheet Drive API ${response.code()}: $err")
            throw Exception("Failed to delete spreadsheet (${response.code()})")
        }
        dao.deleteById(fileId)
    }

    override suspend fun deleteForm(formId: String): Result<Unit> = runCatching {
        val response = driveService.deleteFile(formId)
        if (!response.isSuccessful) {
            val err = response.errorBody()?.string()
            Log.e(TAG, "deleteForm Drive API ${response.code()}: $err")
            throw Exception("Failed to delete form (${response.code()})")
        }
    }

    override suspend fun getFileModifiedTime(spreadsheetId: String): Result<String?> = runCatching {
        val resp = driveService.getFile(spreadsheetId)
        if (resp.isSuccessful) resp.body()?.modifiedTime
        else throw Exception("Failed to get file metadata (${resp.code()})")
    }

    /** Resolves sheetId from spreadsheet metadata. Matches by sheet name when provided. */
    private suspend fun resolveSheetId(spreadsheetId: String, sheetName: String = ""): Int {
        val metaResp = sheetService.getSpreadsheet(spreadsheetId)
        if (!metaResp.isSuccessful) throw Exception("Get metadata failed (${metaResp.code()})")
        val metadata = metaResp.body() ?: throw Exception("Empty metadata body")
        val sheets = metadata.sheets.orEmpty()
        if (sheets.isEmpty()) return 0
        val target = if (sheetName.isBlank()) {
            sheets.firstOrNull()
        } else {
            sheets.firstOrNull { it.properties?.title?.equals(sheetName, ignoreCase = true) == true }
                ?: sheets.firstOrNull()
        }
        return target?.properties?.sheetId ?: 0
    }
}

private fun columnLetter(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) {
        sb.insert(0, ('A' + i % 26))
        i = i / 26 - 1
    }
    return sb.toString()
}

private fun CachedSpreadsheetEntity.toDomain() = Spreadsheet(
    id = id,
    name = name,
    modifiedTime = modifiedTime
)

private fun Spreadsheet.toEntity() = CachedSpreadsheetEntity(
    id = id,
    name = name,
    modifiedTime = modifiedTime,
    createdAt = System.currentTimeMillis(),
    lastSyncedAt = System.currentTimeMillis()
)
