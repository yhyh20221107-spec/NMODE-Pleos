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

private val Blue = Color(0xFFE7E8EB) // neutral accent (buttons and selected states)
private val Dark = Color(0xFFF5F5F6) // primary text
private val Background = Color(0xFF101114) // charcoal canvas
private val CardBg = Color(0xFF1B1D21)
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
    Mode(name = "휴식", category = "휴식", enabled = setOf("temperature", "fan", "ambient", "audio", "seatPosition"), temperature = 23, fan = 1, lightBrightness = 25, audio = "휴식 음악", isTemplate = true),
    Mode(name = "집중", category = "집중", enabled = setOf("temperature", "fan", "ambient", "audio"), temperature = 22, fan = 1, lightBrightness = 60, audio = "집중 음악", isTemplate = true),
    Mode(name = "대기", category = "대기", enabled = setOf("temperature", "seatHeat", "seatVent", "ambient", "audio"), temperature = 23, seatHeat = 1, lightBrightness = 30, isTemplate = true),
    Mode(name = "환기", category = "환기", enabled = setOf("window", "fan", "temperature"), fan = 3, isTemplate = true),
    Mode(name = "겨울철 쾌적", category = "계절", enabled = setOf("temperature", "fan", "seatHeat", "wheelHeat", "defrost"), temperature = 25, fan = 3, seatHeat = 2, isTemplate = true),
    Mode(name = "여름철 쾌적", category = "계절", enabled = setOf("temperature", "fan", "seatVent", "airPurify"), temperature = 21, fan = 3, seatVent = 2, isTemplate = true)
)

private fun Mode.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("category", category)
    put("enabled", JSONArray(enabled.toList())); put("temperature", temperature)
    put("fan", fan); put("seatHeat", seatHeat); put("seatVent", seatVent)
    put("lightBrightness", lightBrightness); put("lightColor", lightColor)
    put("audio", audio); put("isTemplate", isTemplate)
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
                    val a = JSONArray(prefs.getString("modes", "") ?: "")
                    (0 until a.length()).map { modeFromJson(a.getJSONObject(it)) }
                }.getOrElse { templates() })
            }
            fun save(list: List<Mode>) {
                modes = list
                prefs.edit().putString("modes", JSONArray(list.map { it.toJson() }).toString()).apply()
            }
            var screen by remember { mutableStateOf("모드") }
            var editing by remember { mutableStateOf<Mode?>(null) }
            var selected by remember { mutableStateOf<Mode?>(null) }
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
                readVehicleClimate(seat) { key, result ->
                    if (thisRequest == requestId) fields[key] = result
                }
            }
            fun openEditor(mode: Mode) {
                editing = if (mode.isTemplate) mode.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${mode.name} 맞춤",
                    category = "사용자", isTemplate = false
                ) else mode
            }
            fun preview(mode: Mode) {
                selected = mode
                screen = "미리보기"
                refresh()
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Blue,
                    onPrimary = Background,
                    background = Background,
                    onBackground = Dark,
                    surface = CardBg,
                    onSurface = Dark,
                    surfaceVariant = Raised,
                    onSurfaceVariant = Muted,
                    outline = Color(0xFF44464C)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("N:MODE", color = Dark, fontSize = 27.sp,
                                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
                            }
                            Text("VEHICLE MODES", color = Muted,
                                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(15.dp))
                        if (editing != null) {
                            EditorScreen(
                                initial = editing!!,
                                onCancel = { editing = null },
                                onSave = { updated ->
                                    val safe = updated.copy(isTemplate = false, category = "사용자")
                                    val i = modes.indexOfFirst { it.id == safe.id }
                                    save(if (i >= 0) modes.toMutableList().also { it[i] = safe } else modes + safe)
                                    editing = null
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                listOf("모드", "차량 상태").forEach { tab ->
                                    FilterChip(selected = screen == tab, onClick = { screen = tab }, label = { Text(tab) })
                                }
                                if (screen == "미리보기") {
                                    FilterChip(selected = true, onClick = { screen = "모드" }, label = { Text("미리보기") })
                                }
                                Spacer(Modifier.weight(1f))
                                if (screen == "모드") {
                                    Button(onClick = {
                                        editing = Mode(name = "나만의 모드", category = "사용자", enabled = setOf("temperature", "fan"))
                                    }) { Text("+ 모드 만들기") }
                                } else if (screen == "차량 상태") {
                                    Button(onClick = { refresh() }) { Text("상태 새로고침") }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            when (screen) {
                                "모드" -> ModesScreen(modes,
                                    onPreview = { preview(it) },
                                    onEdit = { openEditor(it) },
                                    onDuplicate = {
                                        editing = it.copy(id = UUID.randomUUID().toString(),
                                            name = it.name + " 복사본", isTemplate = false, category = "사용자")
                                    },
                                    onDelete = { target -> save(modes.filterNot { it.id == target.id }) }
                                )
                                "차량 상태" -> VehicleScreen(fields, updatedAt, seat,
                                    onSeat = { seat = it; refresh() }, onRefresh = { refresh() })
                                else -> PreviewScreen(selected, fields,
                                    onBack = { screen = "모드" }, onRefresh = { refresh() },
                                    onEdit = { selected?.let { openEditor(it) } })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModesScreen(
    modes: List<Mode>,
    onPreview: (Mode) -> Unit,
    onEdit: (Mode) -> Unit,
    onDuplicate: (Mode) -> Unit,
    onDelete: (Mode) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("추천 모드", color = Dark, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold) }
        val presets = modes.filter { it.isTemplate }
        items(presets.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { mode ->
                    Box(Modifier.weight(1f)) {
                        ModeTile(mode, onPreview = { onPreview(mode) }, onEdit = { onEdit(mode) },
                            onDuplicate = { onDuplicate(mode) }, onDelete = { onDelete(mode) })
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Spacer(Modifier.height(5.dp))
            Text("내 모드", color = Dark, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        val custom = modes.filterNot { it.isTemplate }
        if (custom.isEmpty()) {
            item {
                Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
                    Text("저장한 모드가 없습니다.", Modifier.fillMaxWidth().padding(17.dp), color = Muted)
                }
            }
        } else {
            items(custom.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { mode ->
                        Box(Modifier.weight(1f)) {
                            ModeTile(mode, onPreview = { onPreview(mode) }, onEdit = { onEdit(mode) },
                                onDuplicate = { onDuplicate(mode) }, onDelete = { onDelete(mode) })
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModeTile(mode: Mode, onPreview: () -> Unit, onEdit: () -> Unit,
                     onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(mode.name, color = Dark, fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mode.enabled.mapNotNull { settingLabels[it] }.take(3).joinToString(" · "),
                color = Muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPreview, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(9.dp)) { Text("미리보기") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(9.dp)) {
                    Text(if (mode.isTemplate) "설정" else "편집")
                }
            }
            if (!mode.isTemplate) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onDuplicate) { Text("복제") }
                    TextButton(onClick = onDelete) { Text("삭제", color = Muted) }
                }
            }
        }
    }
}

@Composable
private fun VehicleScreen(fields: Map<String, String>, lastUpdated: String, seat: SeatValue,
                          onSeat: (SeatValue) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(19.dp)) {
                    Text("현재 차량 환경", color = Dark, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("실시간 조회", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DarkMetric("실내 온도", fields["현재 온도"] ?: "조회 전", Modifier.weight(1f))
                        DarkMetric("송풍 세기", fields["송풍 세기"]?.substringBefore(" · ") ?: "조회 전", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("조회 기준", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(9.dp))
                FilterChip(selected = seat == SeatValue.ROW_1_LEFT,
                    onClick = { onSeat(SeatValue.ROW_1_LEFT) }, label = { Text("1열 왼쪽") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = seat == SeatValue.ROW_1_RIGHT,
                    onClick = { onSeat(SeatValue.ROW_1_RIGHT) }, label = { Text("1열 오른쪽") })
                Spacer(Modifier.weight(1f))
                Text("최근 조회 $lastUpdated", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(listOf("현재 온도", "송풍 세기", "에어컨", "자동 공조")) { label ->
            DataRow(label, fields[label] ?: "조회 전")
        }
        if (fields.containsKey("상태")) item { DataRow("SDK 상태", fields["상태"] ?: "") }
    }
}

@Composable
private fun DarkMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Dark, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(112.dp), color = Muted, fontWeight = FontWeight.Medium)
            Text(value.substringBefore(" · "), modifier = Modifier.weight(1f),
                color = if (value.contains("실패") || value.contains("없음")) Amber else Dark,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PreviewScreen(mode: Mode?, fields: Map<String, String>,
                          onBack: () -> Unit, onRefresh: () -> Unit, onEdit: () -> Unit) {
    if (mode == null) { Text("선택한 모드가 없습니다."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${mode.name} · 적용 미리보기", fontSize = 21.sp,
                        fontWeight = FontWeight.Bold, color = Dark)
                }
                OutlinedButton(onClick = onBack) { Text("모드 목록") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onEdit) { Text("설정 편집") }
            }
        }
        item {
            Surface(color = Raised, shape = RoundedCornerShape(10.dp)) {
                Text("미리보기 전용 · 차량 설정은 변경되지 않습니다.",
                    Modifier.fillMaxWidth().padding(13.dp), color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text("모드 설정", fontWeight = FontWeight.SemiBold, color = Dark) }
        items(mode.enabled.toList()) { key ->
            val current = when (key) {
                "temperature" -> fields["현재 온도"] ?: "조회 전 (현재 실내온도)"
                "fan" -> fields["송풍 세기"]?.substringBefore(" · ") ?: "조회 전"
                "seatHeat", "seatVent", "ambient", "window", "wheelHeat", "defrost", "airPurify", "seatPosition" -> "SDK 미연동"
                else -> "연동 전"
            }
            Surface(color = CardBg, shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(settingLabels[key] ?: key, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("모드 목표", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Text(valueDescription(mode, key), fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("차량 조회", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Text(current, color = Teal, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = onRefresh) { Text("차량 상태 다시 조회") }
                OutlinedButton(onClick = onBack) { Text("다른 모드 선택") }
            }
        }
    }
}

private fun valueDescription(mode: Mode, key: String): String = when (key) {
    "temperature" -> "${mode.temperature}°C"
    "fan" -> "${mode.fan}단계"
    "seatHeat" -> "${mode.seatHeat}단계"
    "seatVent" -> "${mode.seatVent}단계"
    "ambient" -> "${mode.lightColor} · ${mode.lightBrightness}%"
    "audio" -> mode.audio
    else -> "모드 포함 · 상세 제어 미연동"
}

@Composable
private fun EditorScreen(initial: Mode, onCancel: () -> Unit, onSave: (Mode) -> Unit) {
    var mode by remember(initial.id) { mutableStateOf(initial) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("모드 설정", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onCancel) { Text("취소") }
            Button(onClick = { onSave(mode) }, enabled = mode.name.isNotBlank()) { Text("저장하기") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(color = CardBg, shape = RoundedCornerShape(13.dp)) {
                    OutlinedTextField(value = mode.name, onValueChange = { mode = mode.copy(name = it) },
                        label = { Text("모드 이름") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(10.dp))
                }
            }
            items(settingLabels.entries.toList()) { entry ->
                val active = entry.key in mode.enabled
                Surface(color = CardBg, shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.value, modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold)
                            Switch(checked = active, onCheckedChange = { checked ->
                                mode = mode.copy(enabled = if (checked) mode.enabled + entry.key else mode.enabled - entry.key)
                            })
                        }
                        if (active) {
                            when (entry.key) {
                                "temperature" -> Stepper(mode.temperature, 16, 30, "°C") { mode = mode.copy(temperature = it) }
                                "fan" -> Stepper(mode.fan, 0, 8, "단계") { mode = mode.copy(fan = it) }
                                "seatHeat" -> Stepper(mode.seatHeat, 0, 3, "단계") { mode = mode.copy(seatHeat = it) }
                                "seatVent" -> Stepper(mode.seatVent, 0, 3, "단계") { mode = mode.copy(seatVent = it) }
                                "ambient" -> {
                                    Stepper(mode.lightBrightness, 0, 100, "%") { mode = mode.copy(lightBrightness = it) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        listOf("Warm", "Blue", "Purple").forEach { c ->
                                            FilterChip(selected = mode.lightColor == c,
                                                onClick = { mode = mode.copy(lightColor = c) }, label = { Text(c) })
                                        }
                                    }
                                }
                                "audio" -> OutlinedTextField(value = mode.audio, onValueChange = { mode = mode.copy(audio = it) },
                                    label = { Text("오디오 메모 · 미디어 연동 전") }, modifier = Modifier.fillMaxWidth())
                                else -> Text("설정값만 저장", color = Muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$value $unit", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}
