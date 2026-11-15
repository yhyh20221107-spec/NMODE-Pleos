package com.cabinmix.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFFC4B0F5) // neutral accent (buttons and selected states)
private val Dark = Color(0xFFF5F5F6) // primary text
private val Background = Color(0xFF101114) // charcoal canvas
private val CardBg = Color(0xFF1B1D22)
private val Raised = Color(0xFF27292E)
private val Muted = Color(0xFFA8ABB2)
private val Amber = Color(0xFFE6B98A)
private val Teal = Color(0xFFD8DADD)

// 모드 저장값은 시뮬레이션입니다. 차량 설정을 변경하지 않습니다.
data class Mode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val enabled: Set<String>,
    val temperature: Int = 23,
    val fan: Int = 2,
    val seatHeat: Int = 0,
    val seatVent: Int = 0,
    val lightBrightness: Int = 45,
    val lightColor: String = "Warm",
    val audio: String = "잔잔한 음악",
    val windowPercent: Int = 20,
    val wheelHeatOn: Boolean = true,
    val defrostOn: Boolean = true,
    val airPurifyOn: Boolean = true,
    val seatPosition: String = "기본",
    val isTemplate: Boolean = false
)

private val settingLabels = linkedMapOf(
    "temperature" to "공조 온도", "fan" to "송풍 세기",
    "seatHeat" to "시트 열선", "seatVent" to "시트 통풍",
    "ambient" to "앰비언트 무드램프", "audio" to "오디오",
    "window" to "창문 / 환기", "wheelHeat" to "핸들 열선",
    "defrost" to "앞유리 제상", "airPurify" to "공기 청정",
    "seatPosition" to "좌석 자세"
)

private fun templates() = listOf(
    Mode(name = "휴식", category = "휴식", enabled = setOf("temperature", "fan", "ambient", "audio", "seatPosition"), temperature = 23, fan = 1, lightBrightness = 25, audio = "휴식 음악", seatPosition = "휴식", isTemplate = true),
    Mode(name = "집중", category = "집중", enabled = setOf("temperature", "fan", "ambient", "audio"), temperature = 22, fan = 1, lightBrightness = 60, audio = "집중 음악", isTemplate = true),
    Mode(name = "대기", category = "대기", enabled = setOf("temperature", "ambient", "audio"), temperature = 23, seatHeat = 1, lightBrightness = 30, isTemplate = true),
    Mode(name = "환기", category = "환기", enabled = setOf("window", "fan", "temperature"), fan = 3, isTemplate = true),
    Mode(name = "겨울", category = "계절", enabled = setOf("temperature", "fan", "seatHeat", "wheelHeat", "defrost"), temperature = 25, fan = 3, seatHeat = 2, isTemplate = true),
    Mode(name = "여름", category = "계절", enabled = setOf("temperature", "fan", "seatVent", "airPurify"), temperature = 21, fan = 3, seatVent = 2, isTemplate = true)
)

private fun Mode.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("category", category)
    put("enabled", JSONArray(enabled.toList())); put("temperature", temperature)
    put("fan", fan); put("seatHeat", seatHeat); put("seatVent", seatVent)
    put("lightBrightness", lightBrightness); put("lightColor", lightColor)
    put("audio", audio); put("isTemplate", isTemplate)
    put("windowPercent", windowPercent); put("wheelHeatOn", wheelHeatOn)
    put("defrostOn", defrostOn); put("airPurifyOn", airPurifyOn)
    put("seatPosition", seatPosition)
}

private fun modeFromJson(j: JSONObject): Mode {
    val a = j.optJSONArray("enabled") ?: JSONArray()
    return Mode(
        id = j.optString("id", UUID.randomUUID().toString()),
        name = j.optString("name", "이름 없는 모드"),
        category = j.optString("category", "사용자"),
        enabled = (0 until a.length()).map { a.getString(it) }.toSet(),
        temperature = j.optInt("temperature", 23), fan = j.optInt("fan", 2),
        seatHeat = j.optInt("seatHeat", 0), seatVent = j.optInt("seatVent", 0),
        lightBrightness = j.optInt("lightBrightness", 45),
        lightColor = j.optString("lightColor", "Warm"),
        audio = j.optString("audio", "음악"),
        windowPercent = j.optInt("windowPercent", 20),
        wheelHeatOn = j.optBoolean("wheelHeatOn", true),
        defrostOn = j.optBoolean("defrostOn", true),
        airPurifyOn = j.optBoolean("airPurifyOn", true),
        seatPosition = j.optString("seatPosition", "기본"),
        isTemplate = j.optBoolean("isTemplate", false)
    )
}

class MainActivity : ComponentActivity() {
    private var vehicle: Vehicle? = null

    // 지원되지 않는 좌석 영역을 요청한 경우에는 화면에 짧은 상태만 표시합니다.
    // 정확한 예외 내용은 Logcat에서 확인할 수 있습니다.
    private fun queryError(field: String, error: Exception): String {
        Log.w("NMODE_VEHICLE", "$field 조회 실패", error)
        val message = error.message.orEmpty()
        return when {
            message.contains("not supported", ignoreCase = true) &&
                    message.contains("area", ignoreCase = true) -> "요청한 영역에서 조회 실패"
            message.contains("permission", ignoreCase = true) -> "권한 확인 필요"
            message.contains("service", ignoreCase = true) -> "차량 서비스 연결 확인 필요"
            else -> "조회 실패 (${error.javaClass.simpleName})"
        }
    }

    // Capability에 표시된 영역만 읽습니다. 차량 제어(set)는 하지 않습니다.
    private fun supportedAreas(value: Any?): List<SeatArea> {
        // SDK 2.3.2에서 송풍 속도 범위는 Map<SeatArea, AreaBounds<Int>>입니다.
        // 다른 HVAC Capability의 영역 결과도 SDK가 제공한 컬렉션을 기준으로 처리합니다.
        val candidates: Iterable<*> = when (value) {
            is Map<*, *> -> value.keys
            is Iterable<*> -> value
            is Array<*> -> value.asIterable()
            else -> emptyList<Any>()
        }
        return candidates.filterIsInstance<SeatArea>()
    }

    private fun areaDescription(area: SeatArea): String =
        area.values.joinToString(", ") { it.name }.ifEmpty { "공통 영역" }

    private fun chooseArea(value: Any?, preferred: SeatValue): SeatArea? {
        val available = supportedAreas(value)
        return available.firstOrNull { preferred in it.values.toList() }
            ?: available.firstOrNull()
    }

    // read-only: Capability 확인 -> 해당 속성에서 반환한 영역으로 GET
    private fun readVehicleClimate(seat: SeatValue, onField: (String, String) -> Unit) {
        fun update(key: String, value: String) = runOnUiThread { onField(key, value) }
        try {
            if (vehicle == null) {
                val instance = Vehicle(applicationContext)
                instance.initialize()
                vehicle = instance
            }
            val hvac = vehicle?.getHVAC()
            if (hvac == null) {
                update("상태", "공조 서비스 객체를 가져오지 못했습니다")
                return
            }
            val chosenTemperature = SeatArea(seat)
            // 온도 조회는 기존에 성공한 좌석 기반 API를 그대로 사용합니다.
            try {
                hvac.getHvacTemperatureCurrent(
                    area = chosenTemperature,
                    onComplete = { v -> update("현재 온도", if (v == null) "정보 없음" else "$v°C") },
                    onFailed = { e -> update("현재 온도", queryError("현재 온도", e)) }
                )
            } catch (e: Exception) {
                update("현재 온도", queryError("현재 온도", e))
            }

            try {
                hvac.checkHvacFanSpeedCapabilities(
                    onComplete = { _, areaBounds ->
                        val chosen = chooseArea(areaBounds as Any?, seat)
                        if (chosen == null) {
                            update("송풍 세기", "조회 가능한 영역 없음")
                        } else {
                            update("송풍 세기", "조회 중 · ${areaDescription(chosen)}")
                            try {
                                hvac.getHvacFanSpeed(
                                    area = chosen,
                                    onComplete = { v -> update("송풍 세기", v?.let { "${it}단계 · ${areaDescription(chosen)}" } ?: "정보 없음") },
                                    onFailed = { e -> update("송풍 세기", queryError("송풍 세기", e)) }
                                )
                            } catch (e: Exception) {
                                update("송풍 세기", queryError("송풍 세기", e))
                            }
                        }
                    },
                    onFailed = { e -> update("송풍 세기", "지원 범위 확인 실패: ${queryError("송풍", e)}") }
                )
            } catch (e: Exception) {
                update("송풍 세기", "지원 범위 확인 실패: ${queryError("송풍", e)}")
            }

            try {
                hvac.checkHvacAcCapabilities(
                    onComplete = { _, areas ->
                        val chosen = chooseArea(areas as Any?, seat)
                        if (chosen == null) {
                            update("에어컨", "조회 가능한 영역 없음")
                        } else {
                            update("에어컨", "조회 중 · ${areaDescription(chosen)}")
                            try {
                                hvac.getHvacAcState(
                                    area = chosen,
                                    onComplete = { v ->
                                        update("에어컨", when (v) {
                                            true -> "켜짐 · ${areaDescription(chosen)}"
                                            false -> "꺼짐 · ${areaDescription(chosen)}"
                                            else -> "정보 없음"
                                        })
                                    },
                                    onFailed = { e -> update("에어컨", queryError("에어컨", e)) }
                                )
                            } catch (e: Exception) {
                                update("에어컨", queryError("에어컨", e))
                            }
                        }
                    },
                    onFailed = { e -> update("에어컨", "지원 범위 확인 실패: ${queryError("에어컨", e)}") }
                )
            } catch (e: Exception) {
                update("에어컨", "지원 범위 확인 실패: ${queryError("에어컨", e)}")
            }

            try {
                hvac.checkHvacAutoCapabilities(
                    onComplete = { _, areas ->
                        val chosen = chooseArea(areas as Any?, seat)
                        if (chosen == null) {
                            update("자동 공조", "조회 가능한 영역 없음")
                        } else {
                            update("자동 공조", "조회 중 · ${areaDescription(chosen)}")
                            try {
                                hvac.getHvacAutoState(
                                    area = chosen,
                                    onComplete = { v ->
                                        update("자동 공조", when (v) {
                                            true -> "켜짐 · ${areaDescription(chosen)}"
                                            false -> "꺼짐 · ${areaDescription(chosen)}"
                                            else -> "정보 없음"
                                        })
                                    },
                                    onFailed = { e -> update("자동 공조", queryError("자동 공조", e)) }
                                )
                            } catch (e: Exception) {
                                update("자동 공조", queryError("자동 공조", e))
                            }
                        }
                    },
                    onFailed = { e -> update("자동 공조", "지원 범위 확인 실패: ${queryError("자동 공조", e)}") }
                )
            } catch (e: Exception) {
                update("자동 공조", "지원 범위 확인 실패: ${queryError("자동 공조", e)}")
            }
        } catch (e: Exception) {
            update("상태", queryError("SDK 연결", e))
            listOf("현재 온도", "송풍 세기", "에어컨", "자동 공조").forEach {
                update(it, "조회 실패 · 차량 서비스 확인 필요")
            }
        }
    }

    override fun onDestroy() {
        runCatching { vehicle?.release() }
        vehicle = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cabinmix", MODE_PRIVATE)
        setContent {
            var modes by remember {
                mutableStateOf(runCatching {
                    val raw = prefs.getString("modes", null)
                    if (raw.isNullOrBlank()) templates() else {
                        val array = JSONArray(raw)
                        val saved = (0 until array.length()).map { modeFromJson(array.getJSONObject(it)) }
                        val presetNames = listOf("휴식", "집중", "대기", "환기", "겨울", "여름")
                        val savedPresets = saved.filter { it.isTemplate }
                        val normalized = savedPresets.map { mode ->
                            when (mode.name) {
                                "겨울철 쾌적" -> mode.copy(name = "겨울")
                                "여름철 쾌적" -> mode.copy(name = "여름")
                                else -> mode
                            }
                        }
                        val missing = templates().filter { t ->
                            normalized.none { it.name == t.name }
                        }
                        val organized = (normalized + missing).sortedBy { presetNames.indexOf(it.name).let { i -> if (i < 0) 99 else i } }
                        organized + saved.filterNot { it.isTemplate }
                    }
                }.getOrElse { templates() })
            }
            fun save(list: List<Mode>) {
                modes = list
                prefs.edit().putString("modes", JSONArray(list.map { it.toJson() }).toString()).apply()
            }

            // viewingId: currently inspected mode; activeId: explicitly chosen simulated mode.
            var viewingId by remember { mutableStateOf(modes.firstOrNull()?.id) }
            var activeId by remember { mutableStateOf(prefs.getString("active_mode_id", null)) }
            var chooseTarget by remember { mutableStateOf<Mode?>(null) }
            var editing by remember { mutableStateOf<Mode?>(null) }
            var editorOriginal by remember { mutableStateOf<Mode?>(null) }
            var showPreview by remember { mutableStateOf(false) }
            var deleteTarget by remember { mutableStateOf<Mode?>(null) }
            var editorDiscard by remember { mutableStateOf(false) }
            var toast by remember { mutableStateOf("") }
            var seat by remember { mutableStateOf(SeatValue.ROW_1_LEFT) }
            val fields = remember { mutableStateMapOf<String, String>() }
            var updatedAt by remember { mutableStateOf("조회 전") }
            var requestId by remember { mutableIntStateOf(0) }

            fun refresh() {
                requestId += 1
                val thisRequest = requestId
                updatedAt = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
                fields.clear()
                listOf("현재 온도", "송풍 세기", "에어컨", "자동 공조").forEach { fields[it] = "조회 중" }
                readVehicleClimate(seat) { key, value ->
                    if (thisRequest == requestId) fields[key] = value
                }
            }
            fun openNew(mode: Mode, original: Mode? = null) {
                editorOriginal = original
                editing = mode
                showPreview = false
            }
            fun duplicate(mode: Mode) {
                val copied = mode.copy(
                    id = UUID.randomUUID().toString(),
                    name = generateSequence("${mode.name} 복사본", { "$it 복사" }).first { candidate ->
                        modes.none { it.name.equals(candidate, ignoreCase = true) }
                    }, category = "사용자", isTemplate = false
                )
                save(modes + copied)
                viewingId = copied.id
                showPreview = false
                toast = "모드를 복제했습니다"
            }
            val selected = modes.firstOrNull { it.id == viewingId } ?: modes.firstOrNull()

            // Scale N:MODE content only on QHD Automotive displays.
            // The Pleos system bars and other system UI are not affected.
            val originalDensity = LocalDensity.current
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val uiScale = if (screenWidthDp >= 2200) 1.65f else 1f
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = originalDensity.density * uiScale,
                    fontScale = originalDensity.fontScale
                )
            ) {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = Blue, onPrimary = Color(0xFF17131D),
                    background = Background, onBackground = Dark,
                    surface = CardBg, onSurface = Dark,
                    surfaceVariant = Raised, onSurfaceVariant = Muted,
                    outline = Color(0xFF42434A)
                )) {
                    Surface(Modifier.fillMaxSize(), color = Background) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            // 25% mode list, 75% detail; account for inter-panel gap.
                            ModeSidebar(
                                modes = modes, selectedId = selected?.id,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onSelect = { mode -> viewingId = mode.id; editing = null; showPreview = false; toast = "" },
                                onNew = {
                                    openNew(Mode(name = "", category = "사용자", enabled = setOf("temperature", "fan")))
                                }
                            )
                            Surface(
                                modifier = Modifier.weight(3f).fillMaxHeight(),
                                color = CardBg, shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(Modifier.fillMaxSize().padding(22.dp)) {
                                    if (editing != null) {
                                        ModeEditorV5(
                                            initial = editing!!,
                                            existingNames = modes.filter { it.id != editing!!.id }.map { it.name },
                                            onCancel = { changed ->
                                                if (changed) editorDiscard = true else editing = null
                                            },
                                            onSave = { newMode ->
                                                val safe = newMode.copy(name = newMode.name.trim(), category = "사용자", isTemplate = false)
                                                val index = modes.indexOfFirst { it.id == safe.id }
                                                save(if (index < 0) modes + safe else modes.toMutableList().also { it[index] = safe })
                                                viewingId = safe.id
                                                editing = null
                                                editorOriginal = null
                                                toast = "모드를 저장했습니다"
                                            }
                                        )
                                    } else if (selected == null) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("모드를 만들어 주세요", color = Muted)
                                        }
                                    } else if (showPreview) {
                                        ModePreviewV5(mode = selected, fields = fields, lastUpdated = updatedAt,
                                            seat = seat, onSeat = { seat = it; refresh() },
                                            onBack = { showPreview = false }, onRefresh = { refresh() })
                                    } else {
                                        ModeDetailV5(
                                            mode = selected, message = toast,
                                            isActive = selected.id == activeId,
                                            onChoose = { chooseTarget = selected },
                                            onPreview = { showPreview = true; refresh() },
                                            onEdit = {
                                                if (selected.isTemplate) {
                                                    openNew(selected.copy(
                                                        id = UUID.randomUUID().toString(),
                                                        name = "${selected.name} 맞춤",
                                                        isTemplate = false, category = "사용자"
                                                    ))
                                                } else openNew(selected, selected)
                                            },
                                            onDuplicate = { duplicate(selected) },
                                            onDelete = { deleteTarget = selected }
                                        )
                                    }
                                }
                            }
                        }
                        if (chooseTarget != null) {
                            AlertDialog(
                                onDismissRequest = { chooseTarget = null },
                                title = { Text("모드 선택") },
                                text = { Text("'${chooseTarget?.name}' 모드를 사용할 모드로 선택할까요?\n현재 버전에서는 선택 상태만 변경되며 차량 설정은 바뀌지 않습니다.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val target = chooseTarget!!
                                        activeId = target.id
                                        prefs.edit()
                                            .putString("modes", JSONArray(modes.map { it.toJson() }).toString())
                                            .putString("active_mode_id", target.id).apply()
                                        chooseTarget = null
                                        toast = "모드를 선택했습니다 · 차량 제어 없음"
                                    }) { Text("선택") }
                                },
                                dismissButton = { TextButton(onClick = { chooseTarget = null }) { Text("취소", fontSize = 18.sp) } }
                            )
                        }
                        if (deleteTarget != null) {
                            AlertDialog(
                                onDismissRequest = { deleteTarget = null },
                                title = { Text("모드 삭제") },
                                text = { Text("'${deleteTarget?.name}' 모드를 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val target = deleteTarget!!
                                        save(modes.filterNot { it.id == target.id })
                                        viewingId = modes.firstOrNull { it.id != target.id }?.id
                                        if (activeId == target.id) {
                                            activeId = null
                                            prefs.edit().remove("active_mode_id").apply()
                                        }
                                        deleteTarget = null
                                        toast = "모드를 삭제했습니다"
                                    }) { Text("삭제", fontSize = 19.sp) }
                                },
                                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소", fontSize = 18.sp) } }
                            )
                        }
                        if (editorDiscard) {
                            AlertDialog(
                                onDismissRequest = { editorDiscard = false },
                                title = { Text("변경사항 취소") },
                                text = { Text("저장하지 않은 변경사항을 버릴까요?") },
                                confirmButton = { TextButton(onClick = { editing = null; editorDiscard = false }) { Text("버리기") } },
                                dismissButton = { TextButton(onClick = { editorDiscard = false }) { Text("계속 편집") } }
                            )
                        }
                    }
                }
            } // End QHD-only UI scale
        }
    }
}

@Composable
private fun ModeSidebar(modes: List<Mode>, selectedId: String?, modifier: Modifier, onSelect: (Mode) -> Unit, onNew: () -> Unit) {
    Surface(modifier, color = Color(0xFF17181C), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp)) {
            Text("N:MODE", color = Dark, fontSize = 33.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 9.dp, bottom = 26.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { Text("기본 모드", color = Muted, fontSize = 16.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)) }
                items(modes.filter { it.isTemplate }, key = { it.id }) { mode ->
                    ModeNavRow(mode, selectedId == mode.id) { onSelect(mode) }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 13.dp), color = Color(0xFF37383D)) }
                item { Text("내 모드", color = Muted, fontSize = 16.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)) }
                if (modes.none { !it.isTemplate }) item {
                    Text("아직 저장한 모드가 없습니다", color = Muted, fontSize = 15.sp,
                        modifier = Modifier.padding(12.dp))
                }
                items(modes.filterNot { it.isTemplate }, key = { it.id }) { mode ->
                    ModeNavRow(mode, selectedId == mode.id) { onSelect(mode) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text("+ 새 모드", fontWeight = FontWeight.Medium, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ModeNavRow(mode: Mode, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Color(0xFF3A2B4F) else Color.Transparent,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mode.name, color = Dark, fontSize = 21.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ModeDetailV5(mode: Mode, message: String, isActive: Boolean, onChoose: () -> Unit,
                         onPreview: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mode.name, color = Dark, fontSize = 36.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isActive) Text("선택됨", color = Blue, fontSize = 18.sp)
        }
        Spacer(Modifier.height(18.dp))
        Surface(Modifier.weight(1f).fillMaxWidth(), color = Raised,
            shape = RoundedCornerShape(15.dp)) {
            val ordered = settingLabels.keys.filter { it in mode.enabled }
            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("활성화된 설정이 없습니다", color = Muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)) {
                    items(ordered) { key ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 98.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(settingLabels[key] ?: key, color = Muted, fontSize = 21.sp,
                                modifier = Modifier.weight(1f))
                            Text(valueDescription(mode, key), color = Dark, fontSize = 22.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = Color(0xFF3A3B40))
                    }
                }
            }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(message, color = Muted, fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onChoose, enabled = !isActive, modifier = Modifier.weight(1.45f).height(78.dp),
                shape = RoundedCornerShape(11.dp)) { Text(if (isActive) "선택됨" else "이 모드 선택", fontSize = 20.sp) }
            OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1.1f).height(78.dp),
                shape = RoundedCornerShape(11.dp)) { Text("미리보기", fontSize = 19.sp) }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(.9f).height(78.dp),
                shape = RoundedCornerShape(11.dp)) { Text("수정", fontSize = 19.sp) }
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(.9f).height(78.dp),
                shape = RoundedCornerShape(11.dp)) { Text("복제", fontSize = 19.sp) }
            if (!mode.isTemplate) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(.9f).height(78.dp),
                    shape = RoundedCornerShape(11.dp)) { Text("삭제", fontSize = 19.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("선택은 앱 안에서만 반영 · 차량 제어 없음", fontSize = 14.sp, color = Muted)
    }
}

private fun valueDescription(mode: Mode, key: String): String = when (key) {
    "temperature" -> "${mode.temperature}°C"
    "fan" -> "${mode.fan}단계"
    "seatHeat" -> "${mode.seatHeat}단계"
    "seatVent" -> "${mode.seatVent}단계"
    "ambient" -> "${mode.lightColor} · ${mode.lightBrightness}%"
    "audio" -> mode.audio
    "window" -> "${mode.windowPercent}% 열림"
    "wheelHeat" -> if (mode.wheelHeatOn) "켜짐" else "꺼짐"
    "defrost" -> if (mode.defrostOn) "켜짐" else "꺼짐"
    "airPurify" -> if (mode.airPurifyOn) "켜짐" else "꺼짐"
    "seatPosition" -> mode.seatPosition
    else -> "설정 포함"
}

@Composable
private fun ModePreviewV5(mode: Mode, fields: Map<String, String>, lastUpdated: String,
                          seat: SeatValue, onSeat: (SeatValue) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${mode.name} · 미리보기", color = Dark, fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("돌아가기", fontSize = 18.sp) }
        }
        Text("차량 설정은 변경되지 않습니다", color = Muted, fontSize = 17.sp)
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("조회 좌석", color = Muted, fontSize = 17.sp)
            FilterChip(selected = seat == SeatValue.ROW_1_LEFT,
                onClick = { onSeat(SeatValue.ROW_1_LEFT) }, label = { Text("1열 왼쪽") })
            FilterChip(selected = seat == SeatValue.ROW_1_RIGHT,
                onClick = { onSeat(SeatValue.ROW_1_RIGHT) }, label = { Text("1열 오른쪽") })
            Spacer(Modifier.weight(1f))
            Text("최근 조회 $lastUpdated", color = Muted, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("설정", color = Muted, modifier = Modifier.weight(1.2f), fontSize = 17.sp)
            Text("모드 목표", color = Muted, modifier = Modifier.weight(1f), fontSize = 17.sp)
            Text("차량 조회", color = Muted, modifier = Modifier.weight(1f), fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        Surface(Modifier.weight(1f).fillMaxWidth(), color = Raised, shape = RoundedCornerShape(14.dp)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 5.dp)) {
                items(settingLabels.keys.filter { it in mode.enabled }) { key ->
                    val current = when (key) {
                        "temperature" -> fields["현재 온도"] ?: "조회 전"
                        "fan" -> fields["송풍 세기"]?.substringBefore(" · ") ?: "조회 전"
                        else -> "SDK 미연동"
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(settingLabels[key] ?: key, color = Muted, modifier = Modifier.weight(1.2f), fontSize = 19.sp)
                        Text(valueDescription(mode, key), color = Dark, modifier = Modifier.weight(1f), fontSize = 19.sp)
                        Text(current, color = if (current.contains("실패")) Amber else Teal,
                            modifier = Modifier.weight(1f), fontSize = 18.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = Color(0xFF3B3C40))
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("공조 추가 조회", color = Muted, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("에어컨  ${fields["에어컨"]?.substringBefore(" · ") ?: "조회 전"}", color = Dark)
                    Spacer(Modifier.height(8.dp))
                    Text("자동 공조  ${fields["자동 공조"]?.substringBefore(" · ") ?: "조회 전"}", color = Dark)
                    if (fields.containsKey("상태")) {
                        Spacer(Modifier.height(8.dp)); Text(fields["상태"].orEmpty(), color = Amber)
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(11.dp)) { Text("차량 상태 새로고침", fontSize = 19.sp) }
    }
}

@Composable
private fun ModeStepper(value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$value$unit", color = Dark, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 21.sp)
        OutlinedButton(onClick = { onChange((value-1).coerceAtLeast(min)) },
            enabled = value > min, modifier = Modifier.width(84.dp).height(56.dp)) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onChange((value+1).coerceAtMost(max)) },
            enabled = value < max, modifier = Modifier.width(84.dp).height(56.dp)) { Text("+") }
    }
}

@Composable
private fun ModeEditorV5(initial: Mode, existingNames: List<String>, onCancel: (Boolean) -> Unit, onSave: (Mode) -> Unit) {
    var mode by remember(initial.id) { mutableStateOf(initial) }
    val cleanName = mode.name.trim()
    val duplicateName = existingNames.any { it.equals(cleanName, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (initial.name.isBlank()) "새 모드" else "모드 편집", color = Dark,
                fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onCancel(mode != initial) }) { Text("취소", fontSize = 18.sp) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(mode) }, enabled = cleanName.isNotEmpty() && !duplicateName,
                shape = RoundedCornerShape(10.dp)) { Text("저장", fontSize = 19.sp) }
        }
        if (duplicateName) Text("이미 사용 중인 모드 이름입니다", color = Amber, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = mode.name, onValueChange = { mode = mode.copy(name = it.take(24)) },
            singleLine = true, label = { Text("모드 이름") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("사용할 설정만 켜주세요", color = Muted, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(settingLabels.entries.toList()) { entry ->
                val active = entry.key in mode.enabled
                Surface(color = Raised, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.value, color = Dark, modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium, fontSize = 21.sp)
                            Switch(checked = active, onCheckedChange = { checked ->
                                mode = mode.copy(enabled = if (checked) mode.enabled + entry.key else mode.enabled - entry.key)
                            })
                        }
                        if (active) {
                            when (entry.key) {
                                "temperature" -> ModeStepper(mode.temperature,16,30,"°C") { mode = mode.copy(temperature=it) }
                                "fan" -> ModeStepper(mode.fan,0,8,"단계") { mode = mode.copy(fan=it) }
                                "seatHeat" -> ModeStepper(mode.seatHeat,0,3,"단계") { mode = mode.copy(seatHeat=it) }
                                "seatVent" -> ModeStepper(mode.seatVent,0,3,"단계") { mode = mode.copy(seatVent=it) }
                                "ambient" -> {
                                    ModeStepper(mode.lightBrightness,0,100,"%") { mode = mode.copy(lightBrightness=it) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        listOf("Warm", "Blue", "Purple").forEach { color ->
                                            FilterChip(selected = mode.lightColor == color,
                                                onClick = { mode = mode.copy(lightColor = color) },
                                                label = { Text(color) })
                                        }
                                    }
                                }
                                "audio" -> OutlinedTextField(value = mode.audio,
                                    onValueChange = { mode = mode.copy(audio = it) },
                                    label = { Text("오디오 메모 · 미디어 연동 전") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                                "window" -> ModeStepper(mode.windowPercent, 0, 100, "% 열림") {
                                    mode = mode.copy(windowPercent = it)
                                }
                                "wheelHeat" -> ModeChoice(mode.wheelHeatOn, onChange = { mode = mode.copy(wheelHeatOn = it) })
                                "defrost" -> ModeChoice(mode.defrostOn, onChange = { mode = mode.copy(defrostOn = it) })
                                "airPurify" -> ModeChoice(mode.airPurifyOn, onChange = { mode = mode.copy(airPurifyOn = it) })
                                "seatPosition" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("기본", "휴식", "직립").forEach { position ->
                                        FilterChip(selected = mode.seatPosition == position,
                                            onClick = { mode = mode.copy(seatPosition = position) },
                                            label = { Text(position) })
                                    }
                                }
                                else -> Text("세부 설정 미지원", color = Muted, fontSize = 19.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChoice(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true, false).forEach { v ->
            FilterChip(selected = checked == v, onClick = { onChange(v) },
                label = { Text(if (v) "켜짐" else "꺼짐") })
        }
    }
}
