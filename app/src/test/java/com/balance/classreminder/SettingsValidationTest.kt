// 设置草稿校验：校准可独立使用，非法输入不能落盘。
package com.balance.classreminder

import com.balance.classreminder.ui.validateSettingsDraft
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsValidationTest {
    @Test fun calibrationDoesNotRequireTermDate() {
        assertNull(validateSettingsDraft("", "20", "20", "5", true, listOf("08:00" to "08:45")))
    }

    @Test fun restoringDateBasisRequiresValidDate() {
        assertNotNull(validateSettingsDraft("", "20", "20", "", false, emptyList()))
    }

    @Test fun unchangedCalibrationAfterTermDoesNotBlockOtherSettings() {
        assertNull(validateSettingsDraft("", "20", "15", "21", true, emptyList(), preservedCalibrationWeek = 21))
        assertNotNull(validateSettingsDraft("", "20", "15", "22", true, emptyList(), preservedCalibrationWeek = 21))
    }

    @Test fun malformedValuesAreRejected() {
        val invalid = listOf("" to "20", "2a" to "20", "61" to "20", "20" to "1441") // 总周数与提前量边界
        invalid.forEach { (total, reminder) ->
            assertNotNull(validateSettingsDraft("2026-09-07", total, reminder, "5", true, emptyList()))
        }
        assertNotNull(validateSettingsDraft("2026-09-07", "20", "20", "21", true, emptyList()))
    }

    @Test fun invalidTimesAreRejected() {
        listOf("24:00" to "24:30", "08:60" to "09:30", "09:00" to "08:00", "" to "09:00").forEach { period ->
            assertNotNull(validateSettingsDraft("2026-09-07", "20", "20", "", false, listOf(period)))
        }
    }
}
