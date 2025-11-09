package com.example.chonline

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

/**
 * Помощник для отслеживания угла поворота устройства через гироскоп
 */
class GyroscopeHelper(
    private val context: Context,
    private val onAngleChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    // Альтернативные датчики для определения ориентации
    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var initialAngle: Float? = null
    private var currentAngle: Float = 0f
    
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var useAccelerometerMagnetometer = false

    // Сглаживание угловых данных для уменьшения шума
    private var smoothedAngle: Float = 0f
    private val smoothingFactor = 0.15f // Коэффициент сглаживания (0.0 = без сглаживания, 1.0 = полное сглаживание)
    
    // Ограничение частоты обновления UI
    private var lastUpdateTime = 0L
    private val minUpdateInterval = 50L // Минимальный интервал между обновлениями UI (мс)

    private val TAG = "GyroscopeHelper"

    /**
     * Проверить доступные датчики и залогировать их
     */
    private fun checkAvailableSensors() {
        Log.d(TAG, "=== Проверка доступных датчиков ===")
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d(TAG, "Всего датчиков найдено: ${allSensors.size}")
        
        allSensors.forEach { sensor ->
            Log.d(TAG, "Датчик: ${sensor.name}, Тип: ${sensor.type}, Производитель: ${sensor.vendor}")
        }
        
        Log.d(TAG, "TYPE_ROTATION_VECTOR доступен: ${rotationSensor != null}")
        Log.d(TAG, "TYPE_ACCELEROMETER доступен: ${accelerometerSensor != null}")
        Log.d(TAG, "TYPE_MAGNETIC_FIELD доступен: ${magnetometerSensor != null}")
        Log.d(TAG, "===================================")
    }

    /**
     * Начать отслеживание угла поворота
     */
    fun startTracking() {
        // Проверяем доступные датчики
        checkAvailableSensors()
        
        // Пытаемся использовать TYPE_ROTATION_VECTOR (предпочтительный способ)
        rotationSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (success) {
                Log.d(TAG, "✅ Начато отслеживание через TYPE_ROTATION_VECTOR")
                useAccelerometerMagnetometer = false
                return
            } else {
                Log.e(TAG, "❌ Не удалось зарегистрировать TYPE_ROTATION_VECTOR")
            }
        }
        
        // Если TYPE_ROTATION_VECTOR недоступен, используем акселерометр + магнитометр
        if (accelerometerSensor != null && magnetometerSensor != null) {
            val success1 = sensorManager.registerListener(
                this,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            val success2 = sensorManager.registerListener(
                this,
                magnetometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (success1 && success2) {
                Log.d(TAG, "✅ Начато отслеживание через ACCELEROMETER + MAGNETOMETER")
                useAccelerometerMagnetometer = true
                return
            } else {
                Log.e(TAG, "❌ Не удалось зарегистрировать ACCELEROMETER или MAGNETOMETER")
            }
        }
        
        // Если ничего не работает
        Log.e(TAG, "❌❌❌ КРИТИЧЕСКАЯ ОШИБКА: Нет доступных датчиков для определения ориентации!")
        Log.e(TAG, "На этом устройстве невозможно определить ориентацию телефона")
    }

    /**
     * Остановить отслеживание
     */
    fun stopTracking() {
        sensorManager.unregisterListener(this)
        initialAngle = null
        currentAngle = 0f
        Log.d(TAG, "Остановлено отслеживание датчиков")
    }

    /**
     * Сбросить начальный угол (для начала новой панорамы)
     */
    fun resetInitialAngle() {
        initialAngle = null
        currentAngle = 0f
        smoothedAngle = 0f
        lastUpdateTime = 0L
    }

    /**
     * Получить текущий угол поворота от начальной позиции (в градусах)
     */
    fun getCurrentAngle(): Float {
        return currentAngle
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when {
            // Используем TYPE_ROTATION_VECTOR (предпочтительный способ)
            !useAccelerometerMagnetometer && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                processOrientation(rotationMatrix)
            }
            
            // Используем акселерометр + магнитометр (альтернативный способ)
            useAccelerometerMagnetometer && event.sensor.type == Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                calculateOrientationFromAccelMag()
            }
            
            useAccelerometerMagnetometer && event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                calculateOrientationFromAccelMag()
            }
        }
    }
    
    /**
     * Вычислить ориентацию из акселерометра и магнитометра
     */
    private fun calculateOrientationFromAccelMag() {
        val rotationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        if (success) {
            processOrientation(rotationMatrix)
        }
    }
    
    /**
     * Обработать матрицу поворота и вычислить угол
     */
    private fun processOrientation(rotationMatrix: FloatArray) {
        // Получаем ориентацию устройства
        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Азимут (угол поворота вокруг вертикальной оси) в радианах
        val azimuthRad = orientationAngles[0]

        // Устанавливаем начальный угол при первом измерении
        if (initialAngle == null) {
            initialAngle = azimuthRad
            currentAngle = 0f
            smoothedAngle = 0f
            Log.d(TAG, "✅ Начальный угол установлен: ${Math.toDegrees(azimuthRad.toDouble())}°")
        } else {
            // Вычисляем угол поворота от начальной позиции
            var angleDiff = azimuthRad - initialAngle!!

            // Нормализуем угол в диапазон [-π, π]
            while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI.toFloat()
            while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI.toFloat()

            // Конвертируем в градусы и нормализуем в диапазон [0, 360]
            val rawAngle = Math.toDegrees(angleDiff.toDouble()).toFloat()
            val normalizedRawAngle = if (rawAngle < 0) rawAngle + 360f else rawAngle

            // Применяем экспоненциальное сглаживание для уменьшения шума
            // Используем кратчайший путь при переходе через 0/360
            val angleDiffForSmoothing = when {
                abs(normalizedRawAngle - smoothedAngle) > 180 -> {
                    // Переход через границу 0/360 - используем кратчайший путь
                    if (normalizedRawAngle > smoothedAngle) {
                        normalizedRawAngle - 360f - smoothedAngle
                    } else {
                        normalizedRawAngle + 360f - smoothedAngle
                    }
                }
                else -> normalizedRawAngle - smoothedAngle
            }
            
            smoothedAngle += angleDiffForSmoothing * smoothingFactor
            
            // Нормализуем сглаженный угол обратно в [0, 360]
            if (smoothedAngle < 0) smoothedAngle += 360f
            if (smoothedAngle >= 360) smoothedAngle -= 360f
            
            val oldAngle = currentAngle
            currentAngle = smoothedAngle

            // Логируем значительные изменения угла
            if (abs(currentAngle - oldAngle) > 1f) {
                Log.d(TAG, "Угол изменился: $oldAngle° -> $currentAngle° (сырой: $normalizedRawAngle°)")
            }
        }

        // Ограничиваем частоту обновления UI для уменьшения нагрузки
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= minUpdateInterval) {
            lastUpdateTime = currentTime
            onAngleChanged(currentAngle)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не требуется обработка
    }
}

