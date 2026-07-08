package com.leejlredstar.redefinencm.kmp.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Material Symbols（outlined 家族，24px，fill=1；FavoriteBorder 为 fill=0）。
 * 取代已弃用的 compose.materialIconsExtended（冻结在 1.7.3）。
 *
 * Path 数据来自 google/material-design-icons `symbols/web/<name>/materialsymbolsoutlined/`，
 * viewBox 为 `0 -960 960 960`，故用 group(translationY = 960f) 平移到正坐标系。
 */
object AppIcons {
    val Home: ImageVector by lazy {
        symbol("Home", "M160-120v-480l320-240 320 240v480H560v-280H400v280H160Z")
    }
    val Person: ImageVector by lazy {
        symbol("Person", "M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-160v-112q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v112H160Z")
    }
    val Settings: ImageVector by lazy {
        symbol("Settings", "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm112-260q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Z")
    }
    val Search: ImageVector by lazy {
        symbol("Search", "M784-120 532-372q-30 24-69 38t-83 14q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l252 252-56 56ZM380-400q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z")
    }
    val Clear: ImageVector by lazy {
        symbol("Clear", "m256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z")
    }
    val Refresh: ImageVector by lazy {
        symbol("Refresh", "M480-160q-134 0-227-93t-93-227q0-134 93-227t227-93q69 0 132 28.5T720-690v-110h80v280H520v-80h168q-32-56-87.5-88T480-720q-100 0-170 70t-70 170q0 100 70 170t170 70q77 0 139-44t87-116h84q-28 106-114 173t-196 67Z")
    }
    val Delete: ImageVector by lazy {
        symbol("Delete", "M280-120q-33 0-56.5-23.5T200-200v-520h-40v-80h200v-40h240v40h200v80h-40v520q0 33-23.5 56.5T680-120H280Zm80-160h80v-360h-80v360Zm160 0h80v-360h-80v360Z")
    }
    val Check: ImageVector by lazy {
        symbol("Check", "M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z")
    }
    val ArrowBack: ImageVector by lazy {
        symbol("ArrowBack", "m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z", autoMirror = true)
    }
    val ArrowDropDown: ImageVector by lazy {
        symbol("ArrowDropDown", "M480-360 280-560h400L480-360Z")
    }
    val KeyboardArrowLeft: ImageVector by lazy {
        symbol("KeyboardArrowLeft", "M560-240 320-480l240-240 56 56-184 184 184 184-56 56Z", autoMirror = true)
    }
    val KeyboardArrowRight: ImageVector by lazy {
        symbol("KeyboardArrowRight", "M504-480 320-664l56-56 240 240-240 240-56-56 184-184Z", autoMirror = true)
    }
    val PlayArrow: ImageVector by lazy {
        symbol("PlayArrow", "M320-200v-560l440 280-440 280Z")
    }
    val Pause: ImageVector by lazy {
        symbol("Pause", "M560-200v-560h160v560H560Zm-320 0v-560h160v560H240Z")
    }
    val Shuffle: ImageVector by lazy {
        symbol("Shuffle", "M560-160v-80h104L537-367l57-57 126 126v-102h80v240H560Zm-344 0-56-56 504-504H560v-80h240v240h-80v-104L216-160Zm151-377L160-744l56-56 207 207-56 56Z")
    }
    val ShuffleOn: ImageVector by lazy {
        symbol("ShuffleOn", "M120-40q-33 0-56.5-23.5T40-120v-720q0-33 23.5-56.5T120-920h720q33 0 56.5 23.5T920-840v720q0 33-23.5 56.5T840-40H120Zm440-120h240v-240h-80v102L594-424l-57 57 127 127H560v80Zm-344 0 504-504v104h80v-240H560v80h104L160-216l56 56Zm151-377 56-56-207-207-56 56 207 207Z")
    }
    val QueueMusic: ImageVector by lazy {
        symbol("QueueMusic", "M640-160q-50 0-85-35t-35-85q0-50 35-85t85-35q11 0 21 1.5t19 6.5v-328h200v80H760v360q0 50-35 85t-85 35ZM120-320v-80h320v80H120Zm0-160v-80h480v80H120Zm0-160v-80h480v80H120Z", autoMirror = true)
    }
    val Comment: ImageVector by lazy {
        symbol("Comment", "M240-400h480v-80H240v80Zm0-120h480v-80H240v80Zm0-120h480v-80H240v80Zm-80 400q-33 0-56.5-23.5T80-320v-480q0-33 23.5-56.5T160-880h640q33 0 56.5 23.5T880-800v720L720-240H160Z", autoMirror = true)
    }
    val Favorite: ImageVector by lazy {
        symbol("Favorite", "m480-120-58-52q-101-91-167-157T150-447.5Q111-500 95.5-544T80-634q0-94 63-157t157-63q52 0 99 22t81 62q34-40 81-62t99-22q94 0 157 63t63 157q0 46-15.5 90T810-447.5Q771-395 705-329T538-172l-58 52Z")
    }
    val FavoriteBorder: ImageVector by lazy {
        symbol("FavoriteBorder", "m480-120-58-52q-101-91-167-157T150-447.5Q111-500 95.5-544T80-634q0-94 63-157t157-63q52 0 99 22t81 62q34-40 81-62t99-22q94 0 157 63t63 157q0 46-15.5 90T810-447.5Q771-395 705-329T538-172l-58 52Zm0-108q96-86 158-147.5t98-107q36-45.5 50-81t14-70.5q0-60-40-100t-100-40q-47 0-87 26.5T518-680h-76q-15-41-55-67.5T300-774q-60 0-100 40t-40 100q0 35 14 70.5t50 81q36 45.5 98 107T480-228Zm0-273Z")
    }
    val Download: ImageVector by lazy {
        symbol("Download", "M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z")
    }
    val AttachFile: ImageVector by lazy {
        symbol("AttachFile", "M720-330q0 104-73 177T470-80q-104 0-177-73t-73-177v-370q0-75 52.5-127.5T400-880q75 0 127.5 52.5T580-700v350q0 46-32 78t-78 32q-46 0-78-32t-32-78v-370h80v370q0 13 8.5 21.5T470-320q13 0 21.5-8.5T500-350v-350q-1-42-29.5-71T400-800q-42 0-71 29t-29 71v370q-1 71 49 120.5T470-160q70 0 119-49.5T640-330v-390h80v390Z")
    }
    val GraphicEq: ImageVector by lazy {
        symbol("GraphicEq", "M280-240v-480h80v480h-80ZM440-80v-800h80v800h-80ZM120-400v-160h80v160h-80Zm480 160v-480h80v480h-80Zm160-160v-160h80v160h-80Z")
    }
    val QrCode2: ImageVector by lazy {
        symbol("QrCode2", "M520-120v-80h80v80h-80Zm-80-80v-200h80v200h-80Zm320-120v-160h80v160h-80Zm-80-160v-80h80v80h-80Zm-480 80v-80h80v80h-80Zm-80-80v-80h80v80h-80Zm360-280v-80h80v80h-80ZM180-660h120v-120H180v120Zm-60 60v-240h240v240H120Zm60 420h120v-120H180v120Zm-60 60v-240h240v240H120Zm540-540h120v-120H660v120Zm-60 60v-240h240v240H600Zm80 480v-120h-80v-80h160v120h80v80H680ZM520-400v-80h160v80H520Zm-160 0v-80h-80v-80h240v80h-80v80h-80Zm40-200v-160h80v80h80v80H400Zm-190-90v-60h60v60h-60Zm0 480v-60h60v60h-60Zm480-480v-60h60v60h-60Z")
    }
}

private fun symbol(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = autoMirror,
    ).apply {
        group(translationY = 960f) {
            addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        }
    }.build()
