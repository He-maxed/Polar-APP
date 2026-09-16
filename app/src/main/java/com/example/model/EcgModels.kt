package com.example.model

enum class BeatType(val label: String, val code: String) {
    NORMAL("Normal", "N"),
    VEB("Ventricular Ectopic Beat (PVC)", "VEB"),
    SVEB("Supraventricular Ectopic Beat (PAC)", "SVEB"),
    LOW_CONFIDENCE("Low Confidence Beat", "LC"),
    ARTIFACT("Motion / Lead Artifact", "ART")
}

data class BeatAnnotation(
    val sampleIndex: Int,
    val timestampMs: Long,
    val beatType: BeatType,
    val rrIntervalMs: Float,
    val qrsWidthMs: Float,
    val stElevationMv: Float, // in millivolts (1 mm = 0.1 mV)
    val rAmplitudeMv: Float,
    val isCouplet: Boolean = false,
    val isTriplet: Boolean = false,
    val isBigeminy: Boolean = false,
    val isTrigeminy: Boolean = false
)

enum class RhythmEventType(val title: String, val colorHex: Long) {
    VEB_COUPLET("VEB - Couplet", 0xFFFF3B30),
    VEB_TRIPLET("VEB - Triplet", 0xFFFF2D55),
    VEB_RUN("Ventricular Run (VT Suspect)", 0xFFFF7A45),
    SVEB_RUN("SVEB / Atrial Run", 0xFFB48CFF),
    BIGEMINY("Ventricular Bigeminy", 0xFFFF9500),
    TRIGEMINY("Ventricular Trigeminy", 0xFFFFCC00),
    AFIB_SUSPECT("Atrial Fibrillation (Irregular)", 0xFFFF2D7A),
    PAUSE("Sinus Pause / Arrest", 0xFF8E8E93),
    ST_DEPRESSION("ST Depression", 0xFF007AFF),
    ST_ELEVATION("ST Elevation", 0xFF5856D6),
    BRADYCARDIA("Severe Bradycardia", 0xFF34C759),
    TACHYCARDIA("Supraventricular Tachycardia", 0xFFFF9F0A)
}

data class RhythmEvent(
    val id: String,
    val type: RhythmEventType,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val durationSeconds: Float,
    val details: String,
    val sampleStartIndex: Int,
    val sampleEndIndex: Int,
    val motionArtifactScore: Int = 0 // 0 - 100
)

data class FiducialPoint(
    val pOnset: Int = -1,
    val pPeak: Int = -1,
    val pOffset: Int = -1,
    val qPeak: Int = -1,
    val rPeak: Int = -1,
    val sPeak: Int = -1,
    val tPeak: Int = -1,
    val tOffset: Int = -1
)

data class WaveAnalysisItem(
    val name: String,
    val normalRange: String,
    val meanValue: String,
    val percentInNormal: Int,
    val isWarning: Boolean,
    val description: String
)

data class WaveAnalysisResult(
    val qrsDurationMs: Float,
    val prIntervalMs: Float,
    val qtIntervalMs: Float,
    val qtcBazettMs: Float,
    val stLevelMicrovolts: Float,
    val st80Microvolts: Float,
    val rAmplitudeMicrovolts: Float,
    val pWaveDurationMs: Float,
    val items: List<WaveAnalysisItem>,
    val averageMorphology: FloatArray, // normalized representative P-Q-R-S-T complex (e.g. 100 samples)
    val sampleBeats: List<FloatArray> // several superimposed raw beat windows
)

data class HrvResult(
    val rmssdMs: Float,
    val sdnnMs: Float,
    val meanRrMs: Float,
    val averageHrBpm: Float,
    val pnn50Percent: Float,
    val lnRmssd: Float,
    val averageRespiratoryRateBpm: Float,
    val respirationTimeSeries: List<Pair<Long, Float>> // (timestamp, breathsPerMin)
)

data class PhysicalActivityData(
    val steps: Int,
    val distanceMeters: Int,
    val currentVelocityKmh: Float,
    val averageCadenceRpm: Int,
    val velocityTimeSeries: List<Pair<Long, Float>>,
    val cadenceTimeSeries: List<Pair<Long, Float>>,
    val extrasystoleRestPercent: Int,
    val extrasystoleRecoveryPercent: Int,
    val extrasystoleAerobicPercent: Int = 0
)

data class EcgSample(
    val timestampMs: Long,
    val microvolts: Float,
    val filteredMv: Float
)
