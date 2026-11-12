package com.uchet.data.model

/** Два числа нарастающей длины в точке (трубе). */
data class CumulativeLength(
    val pipesOnly: Double,
    val withBha: Double
)

/** Труба + привязанный к ней переводник (null, если после трубы ничего не установлено). */
data class PipeWithCrossover(
    val pipe: PipeEntity,
    val crossover: CrossoverEntity?
)

/** Одна строка сквозной таблицы «Трубы СПО» с накопленной длиной. */
data class SpoPipeRow(
    val pipeId: Long,
    val runId: Long,
    val runNumber: Int,
    val indexInRun: Int,
    val globalNumber: Int,
    val lengthM: Double,
    val diameterLabel: String,
    val crossoverLabel: String,
    val pipesOnly: Double,
    val withBha: Double
)

/** Снимок удалённой трубы для отмены удаления: труба + id оборудования, стоявшего после неё. */
data class DeletedPipe(
    val pipe: PipeEntity,
    val anchoredCrossoverIds: List<Long>
)

/** Снимок удалённой СПО со всеми данными для отмены удаления. */
data class DeletedSpo(
    val spo: SpoEntity,
    val runs: List<RunEntity>,
    val pipes: List<PipeEntity>,
    val crossovers: List<CrossoverEntity>,
    val bha: List<BhaComponentEntity>,
    val wasCurrent: Boolean
)

/** Строка таблицы «Оборудование» в экспорте. */
data class ExportEquipment(
    val number: Int,
    val typeName: String,
    val diameterLabel: String,
    val afterPipe: Int,
    val lengthM: Double,
    val note: String
)

/** Строка сводной статистики по типоразмеру в экспорте. */
data class ExportDiameterRow(
    val name: String,
    val lengthM: Double,
    val count: Int
)

/** Сводка одного ряда для таблицы труб в экспорте. */
data class ExportRunSummary(
    val runNumber: Int,
    val pipeCount: Int,
    val totalLengthM: Double
)

/** Всё, что нужно для построения одного XLSX-файла по одной СПО. */
data class ExportData(
    val spo: SpoEntity,
    val pipes: List<SpoPipeRow>,
    val equipment: List<ExportEquipment>,
    val diameterStats: List<ExportDiameterRow>,
    val bhaComponents: List<BhaComponentEntity>,
    val totalPipes: Int,
    /** Сумма длин только труб (без оборудования и компоновки). */
    val totalLengthM: Double,
    /** Итог по СПО: трубы + всё оборудование, без компоновки (из SpoPipeRowsResult). */
    val totalPipesOnly: Double,
    /** Итог по СПО: трубы + оборудование + компоновка (из SpoPipeRowsResult). */
    val totalWithBha: Double
)

object CrossoverTypes {
    const val TRANSLATOR = 0 // Переводник
    const val TEMPLATE = 1   // Шаблон
    const val CUSTOM = 2     // Свое

    fun typeName(type: Int, customName: String): String = when (type) {
        TRANSLATOR -> "Переводник"
        TEMPLATE -> "Шаблон"
        CUSTOM -> customName.ifBlank { "Свое" }
        else -> "Свое"
    }
}
