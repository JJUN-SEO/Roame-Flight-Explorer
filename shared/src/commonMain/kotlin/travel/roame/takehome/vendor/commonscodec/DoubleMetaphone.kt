/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * THIRD-PARTY CODE — NOT WRITTEN FROM SCRATCH.
 *
 * This file is a Java → Kotlin translation of
 * org.apache.commons.codec.language.DoubleMetaphone from Apache Commons Codec 1.23.0
 * (https://commons.apache.org/proper/commons-codec/, Apache License 2.0). See
 * THIRD_PARTY_NOTICES.md at the repository root.
 *
 * Why a translation instead of a dependency: Commons Codec is a Java library, so it runs on
 * Android and the JVM but not on iOS (Kotlin/Native). Translating it into common Kotlin lets both
 * apps share one implementation.
 *
 * Modifications (Apache-2.0 §4(b)), made 2026-10-30 for Roame Flight Explorer:
 *  - Translated Java syntax to Kotlin. Rule order and every condition are unchanged.
 *  - `maxCodeLen` is an immutable constructor parameter instead of a mutable setter, so an
 *    instance is safe to share between threads.
 *  - Removed the `StringEncoder` / `encode(Object)` plumbing and `EncoderException`.
 *  - `toUpperCase(Locale.ENGLISH)` → `uppercase()` (locale-invariant). `trim()` keeps Java
 *    semantics (strips chars <= ' ').
 *  - Visibility reduced to `internal`.
 *
 * Correctness is checked against the original library, not by hand: `DoubleMetaphoneOracleTest`
 * (shared/src/jvmTest) runs this port and commons-codec side by side on a large word corpus and
 * requires identical primary and alternate codes.
 */

package travel.roame.takehome.vendor.commonscodec

/**
 * Encodes a string into a Double Metaphone value. This Implementation is based on the algorithm by Lawrence
 * Philips.
 *
 * @see <a href="https://drdobbs.com/the-double-metaphone-search-algorithm/184401251?pgno=2">Dr. Dobbs Original Article</a>
 * @see <a href="https://en.wikipedia.org/wiki/Metaphone">Wikipedia Metaphone</a>
 */
internal class DoubleMetaphone(val maxCodeLen: Int = 4) {

    /**
     * Stores results, since there is the optional alternate encoding.
     */
    class DoubleMetaphoneResult(private val maxLength: Int) {

        private val primaryBuilder = StringBuilder(maxLength)
        private val alternateBuilder = StringBuilder(maxLength)

        fun append(value: Char) {
            appendPrimary(value)
            appendAlternate(value)
        }

        fun append(primary: Char, alternate: Char) {
            appendPrimary(primary)
            appendAlternate(alternate)
        }

        fun append(value: String) {
            appendPrimary(value)
            appendAlternate(value)
        }

        fun append(primary: String, alternate: String) {
            appendPrimary(primary)
            appendAlternate(alternate)
        }

        fun appendAlternate(value: Char) {
            if (alternateBuilder.length < maxLength) {
                alternateBuilder.append(value)
            }
        }

        fun appendAlternate(value: String) {
            val addChars = maxLength - alternateBuilder.length
            if (value.length <= addChars) {
                alternateBuilder.append(value)
            } else {
                alternateBuilder.appendRange(value, 0, addChars)
            }
        }

        fun appendPrimary(value: Char) {
            if (primaryBuilder.length < maxLength) {
                primaryBuilder.append(value)
            }
        }

        fun appendPrimary(value: String) {
            val addChars = maxLength - primaryBuilder.length
            if (value.length <= addChars) {
                primaryBuilder.append(value)
            } else {
                primaryBuilder.appendRange(value, 0, addChars)
            }
        }

        val alternate: String get() = alternateBuilder.toString()

        val primary: String get() = primaryBuilder.toString()

        fun isComplete(): Boolean =
            primaryBuilder.length >= maxLength && alternateBuilder.length >= maxLength
    }

    /**
     * Encodes a value with Double Metaphone, optionally using the alternate encoding.
     * Returns `null` for null or blank input.
     */
    fun doubleMetaphone(value: String?, alternate: Boolean = false): String? {
        val cleaned = cleanInput(value) ?: return null

        val slavoGermanic = isSlavoGermanic(cleaned)
        var index = if (isSilentStart(cleaned)) 1 else 0

        val result = DoubleMetaphoneResult(maxCodeLen)

        while (!result.isComplete() && index <= cleaned.length - 1) {
            when (cleaned[index]) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> index = handleAEIOUY(result, index)
                'B' -> {
                    result.append('P')
                    index = if (charAt(cleaned, index + 1) == 'B') index + 2 else index + 1
                }
                'Ç' -> {
                    // C with a Cedilla
                    result.append('S')
                    index++
                }
                'C' -> index = handleC(cleaned, result, index)
                'D' -> index = handleD(cleaned, result, index)
                'F' -> {
                    result.append('F')
                    index = if (charAt(cleaned, index + 1) == 'F') index + 2 else index + 1
                }
                'G' -> index = handleG(cleaned, result, index, slavoGermanic)
                'H' -> index = handleH(cleaned, result, index)
                'J' -> index = handleJ(cleaned, result, index, slavoGermanic)
                'K' -> {
                    result.append('K')
                    index = if (charAt(cleaned, index + 1) == 'K') index + 2 else index + 1
                }
                'L' -> index = handleL(cleaned, result, index)
                'M' -> {
                    result.append('M')
                    index = if (conditionM0(cleaned, index)) index + 2 else index + 1
                }
                'N' -> {
                    result.append('N')
                    index = if (charAt(cleaned, index + 1) == 'N') index + 2 else index + 1
                }
                'Ñ' -> {
                    // N with a tilde (Spanish ene)
                    result.append('N')
                    index++
                }
                'P' -> index = handleP(cleaned, result, index)
                'Q' -> {
                    result.append('K')
                    index = if (charAt(cleaned, index + 1) == 'Q') index + 2 else index + 1
                }
                'R' -> index = handleR(cleaned, result, index, slavoGermanic)
                'S' -> index = handleS(cleaned, result, index, slavoGermanic)
                'T' -> index = handleT(cleaned, result, index)
                'V' -> {
                    result.append('F')
                    index = if (charAt(cleaned, index + 1) == 'V') index + 2 else index + 1
                }
                'W' -> index = handleW(cleaned, result, index)
                'X' -> index = handleX(cleaned, result, index)
                'Z' -> index = handleZ(cleaned, result, index, slavoGermanic)
                else -> index++
            }
        }

        return if (alternate) result.alternate else result.primary
    }

    /**
     * Tests whether the Double Metaphone values of two strings are equal, optionally using the alternate value.
     */
    fun isDoubleMetaphoneEqual(value1: String?, value2: String?, alternate: Boolean = false): Boolean =
        doubleMetaphone(value1, alternate) == doubleMetaphone(value2, alternate)

    /**
     * Gets the character at index `index` if available, or [Char.MIN_VALUE] if out of bounds.
     */
    private fun charAt(value: String, index: Int): Char {
        if (index < 0 || index >= value.length) {
            return Char.MIN_VALUE
        }
        return value[index]
    }

    /**
     * Cleans the input.
     */
    private fun cleanInput(input: String?): String? {
        if (input == null) {
            return null
        }
        val trimmed = input.trim { it <= ' ' }
        if (trimmed.isEmpty()) {
            return null
        }
        return trimmed.uppercase()
    }

    /**
     * Complex condition 0 for 'C'.
     */
    private fun conditionC0(value: String, index: Int): Boolean {
        if (contains(value, index, 4, "CHIA")) {
            return true
        }
        if (index <= 1) {
            return false
        }
        if (isVowel(charAt(value, index - 2))) {
            return false
        }
        if (!contains(value, index - 1, 3, "ACH")) {
            return false
        }
        val c = charAt(value, index + 2)
        return (c != 'I' && c != 'E') ||
            contains(value, index - 2, 6, "BACHER", "MACHER")
    }

    /**
     * Complex condition 0 for 'CH'.
     */
    private fun conditionCH0(value: String, index: Int): Boolean {
        if (index != 0) {
            return false
        }
        if (!contains(value, index + 1, 5, "HARAC", "HARIS") &&
            !contains(value, index + 1, 3, "HOR", "HYM", "HIA", "HEM")
        ) {
            return false
        }
        return !contains(value, 0, 5, "CHORE")
    }

    /**
     * Complex condition 1 for 'CH'.
     */
    private fun conditionCH1(value: String, index: Int): Boolean =
        contains(value, 0, 4, "VAN ", "VON ") || contains(value, 0, 3, "SCH") ||
            contains(value, index - 2, 6, "ORCHES", "ARCHIT", "ORCHID") ||
            contains(value, index + 2, 1, "T", "S") ||
            (
                (contains(value, index - 1, 1, "A", "O", "U", "E") || index == 0) &&
                    (contains(value, index + 2, 1, *L_R_N_M_B_H_F_V_W_SPACE) || index + 1 == value.length - 1)
                )

    /**
     * Complex condition 0 for 'L'.
     */
    private fun conditionL0(value: String, index: Int): Boolean {
        if (index == value.length - 3 &&
            contains(value, index - 1, 4, "ILLO", "ILLA", "ALLE")
        ) {
            return true
        }
        return (
            contains(value, value.length - 2, 2, "AS", "OS") ||
                contains(value, value.length - 1, 1, "A", "O")
            ) &&
            contains(value, index - 1, 4, "ALLE")
    }

    /**
     * Complex condition 0 for 'M'.
     */
    private fun conditionM0(value: String, index: Int): Boolean {
        if (charAt(value, index + 1) == 'M') {
            return true
        }
        return contains(value, index - 1, 3, "UMB") &&
            (index + 1 == value.length - 1 || contains(value, index + 2, 2, "ER"))
    }

    /**
     * Handles 'A', 'E', 'I', 'O', 'U', and 'Y' cases.
     */
    private fun handleAEIOUY(result: DoubleMetaphoneResult, index: Int): Int {
        if (index == 0) {
            result.append('A')
        }
        return index + 1
    }

    /**
     * Handles 'C' cases.
     */
    private fun handleC(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (conditionC0(value, index)) { // very confusing, moved out
            result.append('K')
            index += 2
        } else if (index == 0 && contains(value, index, 6, "CAESAR")) {
            result.append('S')
            index += 2
        } else if (contains(value, index, 2, "CH")) {
            index = handleCH(value, result, index)
        } else if (contains(value, index, 2, "CZ") &&
            !contains(value, index - 2, 4, "WICZ")
        ) {
            //-- "Czerny" --//
            result.append('S', 'X')
            index += 2
        } else if (contains(value, index + 1, 3, "CIA")) {
            //-- "focaccia" --//
            result.append('X')
            index += 3
        } else if (contains(value, index, 2, "CC") &&
            !(index == 1 && charAt(value, 0) == 'M')
        ) {
            //-- double "cc" but not "McClelland" --//
            return handleCC(value, result, index)
        } else if (contains(value, index, 2, "CK", "CG", "CQ")) {
            result.append('K')
            index += 2
        } else if (contains(value, index, 2, "CI", "CE", "CY")) {
            //-- Italian vs. English --//
            if (contains(value, index, 3, "CIO", "CIE", "CIA")) {
                result.append('S', 'X')
            } else {
                result.append('S')
            }
            index += 2
        } else {
            result.append('K')
            if (contains(value, index + 1, 2, " C", " Q", " G")) {
                //-- Mac Caffrey, Mac Gregor --//
                index += 3
            } else if (contains(value, index + 1, 1, "C", "K", "Q") &&
                !contains(value, index + 1, 2, "CE", "CI")
            ) {
                index += 2
            } else {
                index++
            }
        }

        return index
    }

    /**
     * Handles 'CC' cases.
     */
    private fun handleCC(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (contains(value, index + 2, 1, "I", "E", "H") &&
            !contains(value, index + 2, 2, "HU")
        ) {
            //-- "bellocchio" but not "bacchus" --//
            if ((index == 1 && charAt(value, index - 1) == 'A') ||
                contains(value, index - 1, 5, "UCCEE", "UCCES")
            ) {
                //-- "accident", "accede", "succeed" --//
                result.append("KS")
            } else {
                //-- "bacci", "bertucci", other Italian --//
                result.append('X')
            }
            index += 3
        } else { // Pierce's rule
            result.append('K')
            index += 2
        }

        return index
    }

    /**
     * Handles 'CH' cases.
     */
    private fun handleCH(value: String, result: DoubleMetaphoneResult, index: Int): Int {
        if (index > 0 && contains(value, index, 4, "CHAE")) { // Michael
            result.append('K', 'X')
            return index + 2
        }
        if (conditionCH0(value, index)) {
            //-- Greek roots ("chemistry", "chorus", etc.) --//
            result.append('K')
            return index + 2
        }
        if (conditionCH1(value, index)) {
            //-- Germanic, Greek, or otherwise 'ch' for 'kh' sound --//
            result.append('K')
            return index + 2
        }
        if (index > 0) {
            if (contains(value, 0, 2, "MC")) {
                result.append('K')
            } else {
                result.append('X', 'K')
            }
        } else {
            result.append('X')
        }
        return index + 2
    }

    /**
     * Handles 'D' cases.
     */
    private fun handleD(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (contains(value, index, 2, "DG")) {
            //-- "Edge" --//
            if (contains(value, index + 2, 1, "I", "E", "Y")) {
                result.append('J')
                index += 3
                //-- "Edgar" --//
            } else {
                result.append("TK")
                index += 2
            }
        } else if (contains(value, index, 2, "DT", "DD")) {
            result.append('T')
            index += 2
        } else {
            result.append('T')
            index++
        }
        return index
    }

    /**
     * Handles 'G' cases.
     */
    private fun handleG(value: String, result: DoubleMetaphoneResult, startIndex: Int, slavoGermanic: Boolean): Int {
        var index = startIndex
        if (charAt(value, index + 1) == 'H') {
            index = handleGH(value, result, index)
        } else if (charAt(value, index + 1) == 'N') {
            if (index == 1 && isVowel(charAt(value, 0)) && !slavoGermanic) {
                result.append("KN", "N")
            } else if (!contains(value, index + 2, 2, "EY") &&
                charAt(value, index + 1) != 'Y' && !slavoGermanic
            ) {
                result.append("N", "KN")
            } else {
                result.append("KN")
            }
            index += 2
        } else if (contains(value, index + 1, 2, "LI") && !slavoGermanic) {
            result.append("KL", "L")
            index += 2
        } else if (index == 0 &&
            (
                charAt(value, index + 1) == 'Y' ||
                    contains(value, index + 1, 2, *ES_EP_EB_EL_EY_IB_IL_IN_IE_EI_ER)
                )
        ) {
            //-- -ges-, -gep-, -gel-, -gie- at beginning --//
            result.append('K', 'J')
            index += 2
        } else if ((
                contains(value, index + 1, 2, "ER") ||
                    charAt(value, index + 1) == 'Y'
                ) &&
            !contains(value, 0, 6, "DANGER", "RANGER", "MANGER") &&
            !contains(value, index - 1, 1, "E", "I") &&
            !contains(value, index - 1, 3, "RGY", "OGY")
        ) {
            //-- -ger-, -gy- --//
            result.append('K', 'J')
            index += 2
        } else if (contains(value, index + 1, 1, "E", "I", "Y") ||
            contains(value, index - 1, 4, "AGGI", "OGGI")
        ) {
            //-- Italian "biaggi" --//
            if (contains(value, 0, 4, "VAN ", "VON ") ||
                contains(value, 0, 3, "SCH") ||
                contains(value, index + 1, 2, "ET")
            ) {
                //-- obvious germanic --//
                result.append('K')
            } else if (contains(value, index + 1, 3, "IER")) {
                result.append('J')
            } else {
                result.append('J', 'K')
            }
            index += 2
        } else {
            if (charAt(value, index + 1) == 'G') {
                index += 2
            } else {
                index++
            }
            result.append('K')
        }
        return index
    }

    /**
     * Handles 'GH' cases.
     */
    private fun handleGH(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (index > 0 && !isVowel(charAt(value, index - 1))) {
            result.append('K')
            index += 2
        } else if (index == 0) {
            if (charAt(value, index + 2) == 'I') {
                result.append('J')
            } else {
                result.append('K')
            }
            index += 2
        } else if ((index > 1 && contains(value, index - 2, 1, "B", "H", "D")) ||
            (index > 2 && contains(value, index - 3, 1, "B", "H", "D")) ||
            (index > 3 && contains(value, index - 4, 1, "B", "H"))
        ) {
            //-- Parker's rule (with some further refinements) - "hugh"
            index += 2
        } else {
            if (index > 2 && charAt(value, index - 1) == 'U' &&
                contains(value, index - 3, 1, "C", "G", "L", "R", "T")
            ) {
                //-- "laugh", "McLaughlin", "cough", "gough", "rough", "tough"
                result.append('F')
            } else if (index > 0 && charAt(value, index - 1) != 'I') {
                result.append('K')
            }
            index += 2
        }
        return index
    }

    /**
     * Handles 'H' cases.
     */
    private fun handleH(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        //-- only keep if first & before vowel or between 2 vowels --//
        if ((index == 0 || isVowel(charAt(value, index - 1))) &&
            isVowel(charAt(value, index + 1))
        ) {
            result.append('H')
            index += 2
            //-- also takes car of "HH" --//
        } else {
            index++
        }
        return index
    }

    /**
     * Handles 'J' cases.
     */
    private fun handleJ(value: String, result: DoubleMetaphoneResult, startIndex: Int, slavoGermanic: Boolean): Int {
        var index = startIndex
        if (contains(value, index, 4, "JOSE") || contains(value, 0, 4, "SAN ")) {
            //-- obvious Spanish, "Jose", "San Jacinto" --//
            if ((index == 0 && charAt(value, index + 4) == ' ') ||
                value.length == 4 || contains(value, 0, 4, "SAN ")
            ) {
                result.append('H')
            } else {
                result.append('J', 'H')
            }
            index++
        } else {
            if (index == 0 && !contains(value, index, 4, "JOSE")) {
                result.append('J', 'A')
            } else if (isVowel(charAt(value, index - 1)) && !slavoGermanic &&
                (charAt(value, index + 1) == 'A' || charAt(value, index + 1) == 'O')
            ) {
                result.append('J', 'H')
            } else if (index == value.length - 1) {
                result.append('J', ' ')
            } else if (!contains(value, index + 1, 1, *L_T_K_S_N_M_B_Z) &&
                !contains(value, index - 1, 1, "S", "K", "L")
            ) {
                result.append('J')
            }

            if (charAt(value, index + 1) == 'J') {
                index += 2
            } else {
                index++
            }
        }
        return index
    }

    /**
     * Handles 'L' cases.
     */
    private fun handleL(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (charAt(value, index + 1) == 'L') {
            if (conditionL0(value, index)) {
                result.appendPrimary('L')
            } else {
                result.append('L')
            }
            index += 2
        } else {
            index++
            result.append('L')
        }
        return index
    }

    /**
     * Handles 'P' cases.
     */
    private fun handleP(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (charAt(value, index + 1) == 'H') {
            result.append('F')
            index += 2
        } else {
            result.append('P')
            index = if (contains(value, index + 1, 1, "P", "B")) index + 2 else index + 1
        }
        return index
    }

    /**
     * Handles 'R' cases.
     */
    private fun handleR(value: String, result: DoubleMetaphoneResult, index: Int, slavoGermanic: Boolean): Int {
        if (index == value.length - 1 && !slavoGermanic &&
            contains(value, index - 2, 2, "IE") &&
            !contains(value, index - 4, 2, "ME", "MA")
        ) {
            result.appendAlternate('R')
        } else {
            result.append('R')
        }
        return if (charAt(value, index + 1) == 'R') index + 2 else index + 1
    }

    /**
     * Handles 'S' cases.
     */
    private fun handleS(value: String, result: DoubleMetaphoneResult, startIndex: Int, slavoGermanic: Boolean): Int {
        var index = startIndex
        if (contains(value, index - 1, 3, "ISL", "YSL")) {
            //-- special cases "island", "isle", "carlisle", "carlysle" --//
            index++
        } else if (index == 0 && contains(value, index, 5, "SUGAR")) {
            //-- special case "sugar-" --//
            result.append('X', 'S')
            index++
        } else if (contains(value, index, 2, "SH")) {
            if (contains(value, index + 1, 4, "HEIM", "HOEK", "HOLM", "HOLZ")) {
                //-- germanic --//
                result.append('S')
            } else {
                result.append('X')
            }
            index += 2
        } else if (contains(value, index, 3, "SIO", "SIA") || contains(value, index, 4, "SIAN")) {
            //-- Italian and Armenian --//
            if (slavoGermanic) {
                result.append('S')
            } else {
                result.append('S', 'X')
            }
            index += 3
        } else if ((index == 0 && contains(value, index + 1, 1, "M", "N", "L", "W")) ||
            contains(value, index + 1, 1, "Z")
        ) {
            //-- german & anglicisations, for example "smith" match "schmidt" //
            // "snider" match "schneider" --//
            //-- also, -sz- in slavic language although in hungarian it //
            //   is pronounced "s" --//
            result.append('S', 'X')
            index = if (contains(value, index + 1, 1, "Z")) index + 2 else index + 1
        } else if (contains(value, index, 2, "SC")) {
            index = handleSC(value, result, index)
        } else {
            if (index == value.length - 1 && contains(value, index - 2, 2, "AI", "OI")) {
                //-- french for example "resnais", "artois" --//
                result.appendAlternate('S')
            } else {
                result.append('S')
            }
            index = if (contains(value, index + 1, 1, "S", "Z")) index + 2 else index + 1
        }
        return index
    }

    /**
     * Handles 'SC' cases.
     */
    private fun handleSC(value: String, result: DoubleMetaphoneResult, index: Int): Int {
        if (charAt(value, index + 2) == 'H') {
            //-- Schlesinger's rule --//
            if (contains(value, index + 3, 2, "OO", "ER", "EN", "UY", "ED", "EM")) {
                //-- Dutch origin, for example "school", "schooner" --//
                if (contains(value, index + 3, 2, "ER", "EN")) {
                    //-- "schermerhorn", "schenker" --//
                    result.append("X", "SK")
                } else {
                    result.append("SK")
                }
            } else if (index == 0 && !isVowel(charAt(value, 3)) && charAt(value, 3) != 'W') {
                result.append('X', 'S')
            } else {
                result.append('X')
            }
        } else if (contains(value, index + 2, 1, "I", "E", "Y")) {
            result.append('S')
        } else {
            result.append("SK")
        }
        return index + 3
    }

    /**
     * Handles 'T' cases.
     */
    private fun handleT(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (contains(value, index, 4, "TION") || contains(value, index, 3, "TIA", "TCH")) {
            result.append('X')
            index += 3
        } else if (contains(value, index, 2, "TH") || contains(value, index, 3, "TTH")) {
            if (contains(value, index + 2, 2, "OM", "AM") ||
                //-- special case "thomas", "thames" or germanic --//
                contains(value, 0, 4, "VAN ", "VON ") ||
                contains(value, 0, 3, "SCH")
            ) {
                result.append('T')
            } else {
                result.append('0', 'T')
            }
            index += 2
        } else {
            result.append('T')
            index = if (contains(value, index + 1, 1, "T", "D")) index + 2 else index + 1
        }
        return index
    }

    /**
     * Handles 'W' cases.
     */
    private fun handleW(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (contains(value, index, 2, "WR")) {
            //-- can also be in middle of word --//
            result.append('R')
            index += 2
        } else if (index == 0 && (
                isVowel(charAt(value, index + 1)) ||
                    contains(value, index, 2, "WH")
                )
        ) {
            if (isVowel(charAt(value, index + 1))) {
                //-- Wasserman should match Vasserman --//
                result.append('A', 'F')
            } else {
                //-- need Uomo to match Womo --//
                result.append('A')
            }
            index++
        } else if ((index == value.length - 1 && isVowel(charAt(value, index - 1))) ||
            contains(value, index - 1, 5, "EWSKI", "EWSKY", "OWSKI", "OWSKY") ||
            contains(value, 0, 3, "SCH")
        ) {
            //-- Arnow should match Arnoff --//
            result.appendAlternate('F')
            index++
        } else if (contains(value, index, 4, "WICZ", "WITZ")) {
            //-- Polish for example "filipowicz" --//
            result.append("TS", "FX")
            index += 4
        } else {
            index++
        }
        return index
    }

    /**
     * Handles 'X' cases.
     */
    private fun handleX(value: String, result: DoubleMetaphoneResult, startIndex: Int): Int {
        var index = startIndex
        if (index == 0) {
            result.append('S')
            index++
        } else {
            if (!(
                    index == value.length - 1 &&
                        (
                            contains(value, index - 3, 3, "IAU", "EAU") ||
                                contains(value, index - 2, 2, "AU", "OU")
                            )
                    )
            ) {
                //-- French for example breaux --//
                result.append("KS")
            }
            index = if (contains(value, index + 1, 1, "C", "X")) index + 2 else index + 1
        }
        return index
    }

    /**
     * Handles 'Z' cases.
     */
    private fun handleZ(value: String, result: DoubleMetaphoneResult, startIndex: Int, slavoGermanic: Boolean): Int {
        var index = startIndex
        if (charAt(value, index + 1) == 'H') {
            //-- Chinese pinyin for example "zhao" or Angelina "Zhang" --//
            result.append('J')
            index += 2
        } else {
            if (contains(value, index + 1, 2, "ZO", "ZI", "ZA") ||
                (slavoGermanic && index > 0 && charAt(value, index - 1) != 'T')
            ) {
                result.append("S", "TS")
            } else {
                result.append('S')
            }
            index = if (charAt(value, index + 1) == 'Z') index + 2 else index + 1
        }
        return index
    }

    /**
     * Tests whether or not the value starts with a silent letter. It will
     * return `true` if the value starts with any of 'GN', 'KN',
     * 'PN', 'WR' or 'PS'.
     */
    private fun isSilentStart(value: String): Boolean {
        var result = false
        for (element in SILENT_START) {
            if (value.startsWith(element)) {
                result = true
                break
            }
        }
        return result
    }

    /**
     * Tests whether or not a value is of slavo-germanic origin. A value is
     * of Slavo-Germanic origin if it contains any of 'W', 'K', 'CZ', or 'WITZ'.
     */
    private fun isSlavoGermanic(value: String): Boolean =
        value.indexOf('W') > -1 || value.indexOf('K') > -1 ||
            value.contains("CZ") || value.contains("WITZ")

    /**
     * Tests whether or not a character is a vowel or not.
     */
    private fun isVowel(ch: Char): Boolean = VOWELS.indexOf(ch) != -1

    private companion object {
        /**
         * "Vowels" to test.
         */
        const val VOWELS = "AEIOUY"

        /**
         * Prefixes when present which are not pronounced.
         */
        val SILENT_START = arrayOf("GN", "KN", "PN", "WR", "PS")

        val L_R_N_M_B_H_F_V_W_SPACE = arrayOf("L", "R", "N", "M", "B", "H", "F", "V", "W", " ")
        val ES_EP_EB_EL_EY_IB_IL_IN_IE_EI_ER = arrayOf("ES", "EP", "EB", "EL", "EY", "IB", "IL", "IN", "IE", "EI", "ER")
        val L_T_K_S_N_M_B_Z = arrayOf("L", "T", "K", "S", "N", "M", "B", "Z")

        /**
         * Tests whether `value` contains any of the `criteria` starting at index `start` and matching up to
         * length `length`.
         */
        fun contains(value: String, start: Int, length: Int, vararg criteria: String): Boolean {
            var result = false
            if (start >= 0 && start + length <= value.length) {
                val target = value.substring(start, start + length)
                for (element in criteria) {
                    if (target == element) {
                        result = true
                        break
                    }
                }
            }
            return result
        }
    }
}
