package kr.contec.satpass.domain

import kr.contec.satpass.orbit.TLE

/**
 * 사용자가 직접 입력한 TLE 를 검사한다.
 *
 * CelesTrak 에서 받아오는 TLE 와 달리 손으로 넣은 값은 오타가 나기 쉬워서,
 * 저장하기 전에 형식·체크섬·상호 일관성을 확인하고 어디가 잘못됐는지 알려 준다.
 */
object TleValidator {

    /** TLE 한 줄의 표준 길이 */
    private const val LINE_LENGTH = 69

    /** 체크섬이 들어가는 자리 (0-based) */
    private const val CHECKSUM_INDEX = 68

    sealed interface Result {
        /**
         * 검사 통과.
         *
         * @property satelliteName 0번 줄 이름. 입력에 없었으면 `NORAD <번호>` 로 채운다.
         * @property line1 TLE 1번 줄
         * @property line2 TLE 2번 줄
         * @property noradId 카탈로그 번호
         * @property epochMillis TLE epoch
         * @property warnings 저장은 가능하지만 알려 줄 만한 사항
         */
        data class Valid(
            val satelliteName: String,
            val line1: String,
            val line2: String,
            val noradId: Int,
            val epochMillis: Long,
            val warnings: List<String> = emptyList(),
        ) : Result

        /** @property errors 사용자에게 보여 줄 오류 목록 */
        data class Invalid(val errors: List<String>) : Result
    }

    /**
     * @param input 붙여 넣은 TLE 텍스트. 2줄(본문만) 또는 3줄(이름 포함) 모두 받는다.
     * @param fallbackName 이름 줄이 없을 때 쓸 이름. 비어 있으면 `NORAD <번호>` 로 만든다.
     */
    fun validate(input: String, fallbackName: String = ""): Result {
        val lines = input.lineSequence()
            .map { it.trim().trimEnd('\r') }
            .filter { it.isNotEmpty() }
            .toList()

        val (nameLine, line1, line2) = when (lines.size) {
            3 -> Triple(lines[0], lines[1], lines[2])
            2 -> Triple(null, lines[0], lines[1])
            else -> return Result.Invalid(
                listOf("TLE 는 2줄(본문) 또는 3줄(이름 포함)이어야 합니다. 현재 ${lines.size}줄입니다.")
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        errors += checkLine(line1, expectedPrefix = '1', label = "1번 줄")
        errors += checkLine(line2, expectedPrefix = '2', label = "2번 줄")

        if (errors.isNotEmpty()) return Result.Invalid(errors)

        // 두 줄의 카탈로그 번호가 같아야 한다.
        val catalog1 = line1.substring(2, 7).trim()
        val catalog2 = line2.substring(2, 7).trim()
        if (catalog1 != catalog2) {
            errors += "1번 줄과 2번 줄의 카탈로그 번호가 다릅니다. ($catalog1 vs $catalog2)"
        }

        if (errors.isNotEmpty()) return Result.Invalid(errors)

        // 마지막으로 실제 파서에 통과하는지 확인한다.
        val resolvedName = nameLine?.takeIf { it.isNotBlank() }
            ?: fallbackName.takeIf { it.isNotBlank() }
            ?: "NORAD $catalog1"

        val tle = try {
            TLE(arrayOf(resolvedName, line1, line2))
        } catch (e: Exception) {
            return Result.Invalid(
                listOf("TLE 를 해석할 수 없습니다: ${e.localizedMessage ?: e.toString()}")
            )
        }

        if (nameLine == null) {
            warnings += "이름 줄이 없어 '$resolvedName' 으로 저장합니다."
        }

        val epoch = OrbitTime.tleEpochToInstant(tle.epoch)

        return Result.Valid(
            satelliteName = tle.name.ifBlank { resolvedName },
            line1 = line1,
            line2 = line2,
            noradId = tle.catnum,
            epochMillis = epoch.toEpochMilli(),
            warnings = warnings,
        )
    }

    /** 한 줄의 길이·시작 문자·체크섬 검사 */
    private fun checkLine(line: String, expectedPrefix: Char, label: String): List<String> {
        val errors = mutableListOf<String>()

        if (line.length != LINE_LENGTH) {
            errors += "$label 의 길이가 ${line.length}자입니다. (표준: ${LINE_LENGTH}자)"
            // 길이가 다르면 아래 검사들이 의미가 없다.
            return errors
        }

        if (line[0] != expectedPrefix) {
            errors += "$label 은 '$expectedPrefix' 로 시작해야 합니다. (현재 '${line[0]}')"
        }

        val expected = computeChecksum(line)
        val actual = line[CHECKSUM_INDEX]
        if (!actual.isDigit()) {
            errors += "$label 의 체크섬 자리(69번째)가 숫자가 아닙니다. (현재 '$actual')"
        } else if (actual.digitToInt() != expected) {
            errors += "$label 의 체크섬이 맞지 않습니다. (기대값 $expected, 입력값 $actual)"
        }

        return errors
    }

    /**
     * TLE modulo-10 체크섬.
     *
     * 1~68번째 문자를 더하되 숫자는 그 값, 빼기 기호는 1, 나머지(공백·점·문자 등)는 0 으로 센 뒤
     * 10 으로 나눈 나머지를 쓴다.
     */
    fun computeChecksum(line: String): Int {
        var sum = 0
        for (i in 0 until minOf(CHECKSUM_INDEX, line.length)) {
            val c = line[i]
            sum += when {
                c.isDigit() -> c.digitToInt()
                c == '-' -> 1
                else -> 0
            }
        }
        return sum % 10
    }

    /**
     * 체크섬 자리를 올바른 값으로 바꾼 줄을 돌려준다.
     * 사용자가 "체크섬 자동 보정" 을 선택했을 때 쓴다.
     */
    fun withCorrectedChecksum(line: String): String {
        if (line.length != LINE_LENGTH) return line
        return line.substring(0, CHECKSUM_INDEX) + computeChecksum(line).toString()
    }
}
