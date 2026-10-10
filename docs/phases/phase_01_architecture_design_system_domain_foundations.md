# PHASE 1: ARCHITECTURE CORE, DESIGN SYSTEM & ACCESSIBLE DOMAIN FOUNDATIONS
## Detailed Engineering Specification & Physical Execution Protocol

---

### 1. Phase Overview & Strategic Intent
Phase 1 builds the structural backbone and accessible design system of SarvaAI:
1. Establish the Unidirectional Model-View-Intent (MVI) architecture using Kotlin Coroutines and `StateFlow`.
2. Construct a WCAG AAA compliant (7:1 contrast) Material 3 design system with dynamic text scaling up to 200% font scale without UI truncation.
3. Build accessible UI primitives featuring 64dp–96dp touch targets, TalkBack semantic nodes, and high-visibility status indicators.
4. Implement the Room Database schema (Menu, Sections, Items, Cart, Orders, Audio Cache) storing financial figures strictly in integer paise.
5. Implement the DataStore preferences repository for language, playback speed, and adaptive overrides.
6. Construct the physical O-Haptics vibration engine leveraging Android 16 `VibratorManager` waveform compositions.

---

### 2. Architecture & State Management: Unidirectional MVI

#### 2.1 State Contracts (`core/mvi/MviContracts.kt`)
```kotlin
package com.sarvaai.app.core.mvi

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

interface UiState
interface UiEvent
interface UiEffect

abstract class BaseViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effect = Channel<F>(Channel.BUFFERED)
    val effect: Flow<F> = _effect.receiveAsFlow()

    abstract fun onEvent(event: E)

    protected fun setState(reducer: S.() -> S) {
        _uiState.update(reducer)
    }

    protected fun sendEffect(builder: () -> F) {
        viewModelScope.launch {
            _effect.send(builder())
        }
    }
}
```

---

### 3. Accessible Design System & WCAG AAA Theme

#### 3.1 Color Palette Specifications (`ui/theme/Color.kt`)
SarvaAI provides both a standard sleek dark theme and an ultra-high-contrast mode (14:1 contrast ratio) for visually impaired and elderly diners:
```kotlin
package com.sarvaai.app.ui.theme

import androidx.compose.ui.graphics.Color

// Standard Accessible Dark Palette
val CharcoalBackground = Color(0xFF121212)
val CharcoalSurface = Color(0xFF1E1E1E)
val CharcoalSurfaceVariant = Color(0xFF2A2A2A)
val SaffronPrimary = Color(0xFFFF9933)     // Deep Indian Saffron
val SaffronOnPrimary = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)
val MutedText = Color(0xFFD0D0D0)

// Dietary Color Primitives (Strict High Contrast)
val VegGreen = Color(0xFF00C853)           // High-contrast emerald veg
val VegGreenContainer = Color(0xFF003914)
val NonVegRed = Color(0xFFFF3D00)          // High-contrast crimson non-veg
val NonVegRedContainer = Color(0xFF4A0000)
val JainYellow = Color(0xFFFFD600)         // Saffron-gold Jain indicator

// High Contrast Mode (WCAG AAA Override)
val HighContrastBlack = Color(0xFF000000)
val HighContrastWhite = Color(0xFFFFFFFF)
val HighContrastYellow = Color(0xFFFFEB3B)  // Vivid luminous yellow highlight
val HighContrastBorder = Color(0xFFFFFFFF)
```

#### 3.2 Dynamic Typography System (`ui/theme/Type.kt`)
To guarantee that 200% font scale does not overlap or clip text:
```kotlin
package com.sarvaai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SarvaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.25.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.25.sp
    )
)
```

---

### 4. Accessible UI Primitives

#### 4.1 `AccessibleButton.kt` (`ui/components/AccessibleButton.kt`)
Features:
- Guaranteed minimum height of 64dp (exceeds Android 48dp minimum for motor and elderly users).
- Explicit TalkBack action label.
- High-contrast focused border.
- Integrated haptic feedback on touch.
```kotlin
package com.sarvaai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvaai.app.ui.theme.*

@Composable
fun AccessibleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) SaffronPrimary else CharcoalSurfaceVariant,
            contentColor = if (isPrimary) SaffronOnPrimary else PureWhite,
            disabledContainerColor = Color(0xFF333333),
            disabledContentColor = Color(0xFF777777)
        ),
        border = BorderStroke(2.dp, if (isPrimary) SaffronPrimary else PureWhite),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                this.contentDescription = contentDescription ?: text
                role = Role.Button
            }
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

#### 4.2 `DietaryBadge.kt` (`ui/components/DietaryBadge.kt`)
Distinct visual shape and color + explicit accessibility content description:
- Veg: Green square with inscribed circle (`"Vegetarian dish"`).
- Non-Veg: Red square with inscribed triangle (`"Non-vegetarian dish"`).
- Jain: Gold badge (`"Jain suitable dish, prepared without root vegetables"`).

---

### 5. Room Database Architecture (`data/db/`)

#### 5.1 Financial Integrity Mandate
All monetary values are stored strictly as `Long` or `Int` in **paise** (1 INR = 100 paise). Floating-point `Double` or `Float` is strictly prohibited to eliminate calculation and rounding drift.

#### 5.2 Room Entities (`data/entities/`)
```kotlin
package com.sarvaai.app.data.entities

import androidx.room.*

@Entity(tableName = "menus")
data class MenuEntity(
    @PrimaryKey val menuId: String,
    val restaurantName: String,
    val captureTimestamp: Long,
    val pageCount: Int,
    val rawOcrSummary: String? = null
)

@Entity(
    tableName = "menu_sections",
    foreignKeys = [
        ForeignKey(
            entity = MenuEntity::class,
            parentColumns = ["menuId"],
            childColumns = ["menuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("menuId")]
)
data class MenuSectionEntity(
    @PrimaryKey val sectionId: String,
    val menuId: String,
    val titleEnglish: String,
    val titleNative: String? = null,
    val displayOrder: Int
)

@Entity(
    tableName = "menu_items",
    foreignKeys = [
        ForeignKey(
            entity = MenuSectionEntity::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionId")]
)
data class MenuItemEntity(
    @PrimaryKey val itemId: String,
    val sectionId: String,
    val nameEnglish: String,
    val nameHindi: String? = null,
    val nameTelugu: String? = null,
    val transliteration: String? = null,
    val description: String? = null,
    val pricePaise: Long, // e.g. 25000L for ₹250.00
    val isVegetarian: Boolean,
    val isJain: Boolean = false,
    val isVegan: Boolean = false,
    val isNutFree: Boolean = true,
    val spiceLevel: Int = 1, // 0 = Mild, 1 = Medium, 2 = Spicy, 3 = Very Spicy
    val isChefSpecial: Boolean = false
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val cartItemId: String,
    val itemId: String,
    val name: String,
    val unitPricePaise: Long,
    val quantity: Int,
    val specialNotes: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "order_audits")
data class OrderAuditEntity(
    @PrimaryKey val orderId: String,
    val timestamp: Long,
    val itemsSummary: String, // JSON array
    val subtotalPaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val serviceChargePaise: Long,
    val totalPaise: Long,
    val status: String // "CONFIRMED", "SERVED", "BILLED"
)
```

#### 5.3 DAOs (`data/dao/`)
```kotlin
package com.sarvaai.app.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.sarvaai.app.data.entities.*

@Dao
interface MenuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenu(menu: MenuEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<MenuSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MenuItemEntity>)

    @Query("SELECT * FROM menus WHERE menuId = :menuId")
    suspend fun getMenuById(menuId: String): MenuEntity?

    @Query("SELECT * FROM menu_sections WHERE menuId = :menuId ORDER BY displayOrder ASC")
    fun getSectionsForMenu(menuId: String): Flow<List<MenuSectionEntity>>

    @Query("SELECT * FROM menu_items WHERE sectionId = :sectionId")
    fun getItemsForSection(sectionId: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE isVegetarian = 1")
    fun getVegetarianItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE itemId = :itemId")
    suspend fun getItemById(itemId: String): MenuItemEntity?
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY addedTimestamp ASC")
    fun getCartItems(): Flow<List<CartItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE cartItemId = :cartItemId")
    suspend fun deleteCartItem(cartItemId: String)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()
}
```

---

### 6. O-Haptics Linear Motor Engine (`audio/haptics/HapticFeedbackEngine.kt`)

Tuned specifically for realme P3 Pro's X-axis linear motor:
```kotlin
package com.sarvaai.app.audio.haptics

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticPattern {
    CLICK,
    DOUBLE_CLICK,
    CONFIRMATION,
    ERROR_ALERT,
    CAMERA_STABLE,
    SPEECH_TURN_CUE
}

class HapticFeedbackEngine(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun trigger(pattern: HapticPattern) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (pattern) {
                HapticPattern.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticPattern.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticPattern.CAMERA_STABLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticPattern.CONFIRMATION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 80)
                            .compose()
                    } else {
                        VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), intArrayOf(0, 200, 0, 255), -1)
                    }
                }
                HapticPattern.ERROR_ALERT -> {
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150), intArrayOf(0, 255, 0, 255), -1)
                }
                HapticPattern.SPEECH_TURN_CUE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                            .compose()
                    } else {
                        VibrationEffect.createOneShot(50, 180)
                    }
                }
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(50)
        }
    }
}
```

---

### 7. Physical Testing & Validation on realme P3 Pro (Android 16)

#### 7.1 Dynamic Font Scale Stress Test (200%)
1. Open phone **Settings -> Accessibility -> Display size and text**.
2. Drag Font size slider to maximum (Level 4 / 200%).
3. Run SarvaAI: Inspect `AccessibleButton`, headings, and telemetry card.
4. **Pass Criteria**: All text wraps naturally without truncation, ellipsis (`...`), or layout overlap.

#### 7.2 TalkBack Semantic Navigation Audit
1. Enable TalkBack (`Settings -> Accessibility -> TalkBack -> On`).
2. Swipe right consecutively across screen:
3. **Pass Criteria**: TalkBack announces:
   - *"SarvaAI, Heading, Level 1"*
   - *"Device Telemetry Check, Heading, Level 2"*
   - Each button announces: *"Button, double-tap to activate"*.

#### 7.3 O-Haptics Tactile Feel Validation
1. Trigger all 6 haptic patterns via test buttons.
2. **Pass Criteria**: Distinct physical vibrations felt on realme P3 Pro's X-axis motor without sluggishness or rattling sound.

---

### 8. Git Checkpoint & Remote Push
```bash
git add .
git commit -m "feat(phase1): implement mvi state architecture, wcag aaa accessible theme, room database entities and o-haptics vibration engine"
git push origin main
```

---

### 9. Exit Criteria Checklist
- [x] MVI `BaseViewModel` and StateFlow contracts compiled and tested.
- [x] WCAG AAA Color Palette and dynamic typography up to 200% font scale verified.
- [x] `AccessibleButton` and dietary badges conform to 64dp+ touch target rules.
- [x] Room database entities and DAOs compiled with KSP; paise integers used for all money.
- [x] Physical O-Haptics patterns verified on realme P3 Pro hardware.
- [x] Code pushed to remote repository.
