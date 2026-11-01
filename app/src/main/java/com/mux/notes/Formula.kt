package com.mux.notes

import java.util.Locale

class SheetError(val m: String) : Exception(m)

/** Cell values. Err never leaves [SheetCalc]: formula errors are thrown as [SheetError] and stored as Err. */
sealed class V {
    object Blank : V()
    class Num(val d: Double) : V()
    class Str(val s: String) : V()
    class Err(val m: String) : V()
    class Rng(val refs: List<String>) : V()
}

object Sheet {
    const val MAX_COLS = 26

    fun colName(c: Int): String = ('A' + c).toString()
    fun ref(r: Int, c: Int): String = "${colName(c)}${r + 1}"

    /** "B12" -> (row 11, col 1), or null when it is not a cell reference. */
    fun parseRef(s: String): Pair<Int, Int>? {
        val t = s.replace("$", "").uppercase()
        val m = Regex("^([A-Z])(\\d+)$").matchEntire(t) ?: return null
        val row = m.groupValues[2].toIntOrNull() ?: return null
        if (row < 1) return null
        return (row - 1) to (m.groupValues[1][0] - 'A')
    }

    fun fmtNum(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "#NUM"
        if (Math.abs(d) >= 1e15) return String.format(Locale.US, "%.4E", d)
        var s = String.format(Locale.US, "%,.2f", d)
        if (s.endsWith(".00")) s = s.dropLast(3) else if (s.endsWith("0")) s = s.dropLast(1)
        if (s == "-0") s = "0"
        return s
    }

    /** Copy a formula to another cell: relative references move by (dr, dc), $ references stay. */
    fun shiftFormula(f: String, dr: Int, dc: Int): String {
        if (!f.startsWith("=")) return f
        val re = Regex("(\\$?)([A-Z])(\\$?)(\\d+)")
        return re.replace(f) { m ->
            val colAbs = m.groupValues[1] == "$"
            val rowAbs = m.groupValues[3] == "$"
            var c = m.groupValues[2][0] - 'A'
            var r = m.groupValues[4].toInt()
            if (!colAbs) c += dc
            if (!rowAbs) r += dr
            if (c < 0 || c >= MAX_COLS || r < 1) "#REF!" else
                (if (colAbs) "$" else "") + colName(c) + (if (rowAbs) "$" else "") + r
        }
    }

    /** The monthly budget template: income, expense, running balance, summary and per category totals. */
    fun budgetTemplate(): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        val heads = listOf("DATE", "CATEGORY", "DESCRIPTION", "INCOME", "EXPENSE", "BALANCE")
        heads.forEachIndexed { c, h -> m[ref(0, c)] = h }
        val sample = listOf(
            listOf("1", "Salary", "Monthly salary", "50000", ""),
            listOf("2", "Rent", "House rent", "", "12000"),
            listOf("3", "Food", "Groceries", "", "6500"),
            listOf("5", "Transport", "Bus and fuel", "", "1800"),
            listOf("8", "Bills", "Internet and electricity", "", "2400")
        )
        sample.forEachIndexed { i, row -> row.forEachIndexed { c, v -> if (v.isNotEmpty()) m[ref(i + 1, c)] = v } }
        for (r in 1..31) {
            m[ref(r, 5)] = if (r == 1) "=D2-E2" else "=F${r}+D${r + 1}-E${r + 1}"
        }
        m["H1"] = "SUMMARY"
        m["H2"] = "TOTAL INCOME"; m["I2"] = "=SUM(D2:D32)"
        m["H3"] = "TOTAL EXPENSE"; m["I3"] = "=SUM(E2:E32)"
        m["H4"] = "BALANCE"; m["I4"] = "=I2-I3"
        m["H5"] = "SAVINGS %"; m["I5"] = "=IF(I2>0,I4/I2*100,0)"
        m["H7"] = "CATEGORY"; m["I7"] = "SPENT"
        listOf("Food", "Rent", "Transport", "Bills", "Health", "Other").forEachIndexed { i, cat ->
            val r = 8 + i
            m["H$r"] = cat
            m["I$r"] = "=SUMIF(\$B\$2:\$B\$32,H$r,\$E\$2:\$E\$32)"
        }
        return m
    }
    const val BUDGET_ROWS = 34
    const val BUDGET_COLS = 9
}

/** Evaluates a sheet. One instance per cell map, results are memoised. */
class SheetCalc(private val cells: Map<String, String>) {
    private val memo = HashMap<String, V>()
    private val stack = HashSet<String>()

    fun raw(ref: String): String = cells[ref] ?: ""

    fun value(ref: String): V {
        memo[ref]?.let { return it }
        if (!stack.add(ref)) return V.Err("#CYCLE")
        val raw = raw(ref)
        val v: V = when {
            raw.isBlank() -> V.Blank
            raw.startsWith("=") -> try { P(raw.substring(1)).parse() } catch (e: SheetError) { V.Err(e.m) }
            else -> literal(raw)
        }
        stack.remove(ref)
        memo[ref] = v
        return v
    }

    private fun literal(raw: String): V {
        val t = raw.trim()
        if (Regex("^-?[0-9][0-9,]*(\\.[0-9]+)?$").matches(t)) t.replace(",", "").toDoubleOrNull()?.let { return V.Num(it) }
        if (Regex("^-?[0-9]+(\\.[0-9]+)?%$").matches(t)) t.dropLast(1).toDoubleOrNull()?.let { return V.Num(it / 100.0) }
        if (Regex("^-?\\.[0-9]+$").matches(t)) t.toDoubleOrNull()?.let { return V.Num(it) }
        return V.Str(raw)
    }

    fun display(ref: String): String = when (val v = value(ref)) {
        is V.Blank -> ""
        is V.Num -> Sheet.fmtNum(v.d)
        is V.Str -> v.s
        is V.Err -> v.m
        is V.Rng -> "#VALUE"
    }

    fun number(ref: String): Double? = (value(ref) as? V.Num)?.d

    fun isError(ref: String): Boolean = value(ref) is V.Err

    // ------------------------------------------------------------------ parser

    private inner class P(val s: String) {
        var i = 0

        fun parse(): V {
            val v = cmp()
            ws()
            if (i < s.length) throw SheetError("#ERR")
            return when (v) {
                is V.Rng -> throw SheetError("#VALUE")
                else -> v
            }
        }

        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun peek(): Char { ws(); return if (i < s.length) s[i] else '\u0000' }

        fun num(v: V): Double = when (v) {
            is V.Num -> v.d
            is V.Blank -> 0.0
            is V.Str -> v.s.trim().toDoubleOrNull() ?: throw SheetError("#VALUE")
            is V.Err -> throw SheetError(v.m)
            is V.Rng -> throw SheetError("#VALUE")
        }

        fun cmp(): V {
            val l = add()
            ws()
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
            val op: String = when {
                two == "<=" || two == ">=" || two == "<>" -> two
                peek() == '=' || peek() == '<' || peek() == '>' -> s[i].toString()
                else -> return l
            }
            i += op.length
            val r = add()
            val c: Int = if (isNumberLike(l) && isNumberLike(r)) {
                num(l).compareTo(num(r))
            } else {
                text(l).compareTo(text(r), ignoreCase = true)
            }
            val res = when (op) {
                "=" -> c == 0
                "<>" -> c != 0
                "<" -> c < 0
                ">" -> c > 0
                "<=" -> c <= 0
                else -> c >= 0
            }
            return V.Num(if (res) 1.0 else 0.0)
        }

        fun isNumberLike(v: V) = v is V.Num || v is V.Blank
        fun text(v: V): String = when (v) {
            is V.Str -> v.s
            is V.Num -> Sheet.fmtNum(v.d)
            else -> ""
        }

        fun add(): V {
            var l = mul()
            while (true) {
                val c = peek()
                if (c == '+') { i++; l = V.Num(num(l) + num(mul())) }
                else if (c == '-') { i++; l = V.Num(num(l) - num(mul())) }
                else break
            }
            return l
        }

        fun mul(): V {
            var l = pow()
            while (true) {
                val c = peek()
                if (c == '*') { i++; l = V.Num(num(l) * num(pow())) }
                else if (c == '/') {
                    i++
                    val d = num(pow())
                    if (d == 0.0) throw SheetError("#DIV/0")
                    l = V.Num(num(l) / d)
                } else break
            }
            return l
        }

        fun pow(): V {
            val b = unary()
            if (peek() == '^') {
                i++
                return V.Num(Math.pow(num(b), num(unary())))
            }
            return b
        }

        fun unary(): V {
            val c = peek()
            if (c == '-') { i++; return V.Num(-num(unary())) }
            if (c == '+') { i++; return unary() }
            var v = primary()
            while (peek() == '%') { i++; v = V.Num(num(v) / 100.0) }
            return v
        }

        fun primary(): V {
            val c = peek()
            when {
                c == '(' -> {
                    i++
                    val v = cmp()
                    if (peek() != ')') throw SheetError("#ERR")
                    i++
                    return v
                }
                c.isDigit() || c == '.' -> {
                    val st = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    return V.Num(s.substring(st, i).toDoubleOrNull() ?: throw SheetError("#ERR"))
                }
                c == '"' -> {
                    i++
                    val sb = StringBuilder()
                    while (true) {
                        if (i >= s.length) throw SheetError("#ERR")
                        if (s[i] == '"') {
                            if (i + 1 < s.length && s[i + 1] == '"') { sb.append('"'); i += 2; continue }
                            i++
                            break
                        }
                        sb.append(s[i]); i++
                    }
                    return V.Str(sb.toString())
                }
                c.isLetter() || c == '$' -> {
                    val st = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '$' || s[i] == '_')) i++
                    val word = s.substring(st, i)
                    if (peek() == '(' && word.all { it.isLetter() }) {
                        i++
                        return call(word.uppercase(), args())
                    }
                    val ref = word.replace("$", "").uppercase()
                    val rc = Sheet.parseRef(ref) ?: throw SheetError("#NAME")
                    if (peek() == ':') {
                        i++
                        ws()
                        val st2 = i
                        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '$')) i++
                        val rc2 = Sheet.parseRef(s.substring(st2, i)) ?: throw SheetError("#REF")
                        val refs = ArrayList<String>()
                        for (r in minOf(rc.first, rc2.first)..maxOf(rc.first, rc2.first)) {
                            for (cc in minOf(rc.second, rc2.second)..maxOf(rc.second, rc2.second)) refs.add(Sheet.ref(r, cc))
                        }
                        return V.Rng(refs)
                    }
                    val v = value(ref)
                    if (v is V.Err) throw SheetError(v.m)
                    return v
                }
            }
            throw SheetError("#ERR")
        }

        fun args(): List<V> {
            val out = ArrayList<V>()
            if (peek() == ')') { i++; return out }
            while (true) {
                out.add(cmp())
                val c = peek()
                if (c == ',' || c == ';') { i++; continue }
                if (c == ')') { i++; break }
                throw SheetError("#ERR")
            }
            return out
        }

        fun nums(args: List<V>): List<Double> {
            val out = ArrayList<Double>()
            for (a in args) {
                when (a) {
                    is V.Num -> out.add(a.d)
                    is V.Rng -> for (r in a.refs) {
                        val v = value(r)
                        if (v is V.Num) out.add(v.d) else if (v is V.Err) throw SheetError(v.m)
                    }
                    is V.Str -> out.add(a.s.trim().toDoubleOrNull() ?: throw SheetError("#VALUE"))
                    is V.Err -> throw SheetError(a.m)
                    is V.Blank -> {}
                }
            }
            return out
        }

        fun matcher(c: V): (V) -> Boolean {
            if (c is V.Num) return { v -> v is V.Num && v.d == c.d }
            val cs = text(c).trim()
            var op = "="
            var rest = cs
            for (o in listOf("<=", ">=", "<>", "<", ">", "=")) {
                if (cs.startsWith(o)) { op = o; rest = cs.substring(o.length).trim(); break }
            }
            val target = rest.replace(",", "").toDoubleOrNull()
            return { v ->
                if (target != null && v is V.Num) {
                    val d = v.d.compareTo(target)
                    when (op) { "=" -> d == 0; "<>" -> d != 0; "<" -> d < 0; ">" -> d > 0; "<=" -> d <= 0; else -> d >= 0 }
                } else {
                    val t = when (v) { is V.Str -> v.s; is V.Num -> Sheet.fmtNum(v.d); else -> "" }
                    val e = t.trim().equals(rest, ignoreCase = true)
                    if (op == "<>") !e else e && (op == "=")
                }
            }
        }

        fun refs(v: V): List<String> = if (v is V.Rng) v.refs else throw SheetError("#VALUE")

        fun call(name: String, a: List<V>): V {
            fun need(n: Int) { if (a.size < n) throw SheetError("#ARGS") }
            return when (name) {
                "SUM" -> V.Num(nums(a).sum())
                "AVERAGE", "AVG" -> { val n = nums(a); if (n.isEmpty()) throw SheetError("#DIV/0"); V.Num(n.sum() / n.size) }
                "MIN" -> V.Num(nums(a).minOrNull() ?: 0.0)
                "MAX" -> V.Num(nums(a).maxOrNull() ?: 0.0)
                "COUNT" -> V.Num(nums(a).size.toDouble())
                "ABS" -> { need(1); V.Num(Math.abs(num(a[0]))) }
                "SQRT" -> { need(1); val d = num(a[0]); if (d < 0) throw SheetError("#NUM"); V.Num(Math.sqrt(d)) }
                "ROUND" -> {
                    need(1)
                    val digits = if (a.size > 1) num(a[1]).toInt() else 0
                    val p = Math.pow(10.0, digits.toDouble())
                    V.Num(Math.round(num(a[0]) * p) / p)
                }
                "IF" -> {
                    need(2)
                    val cond = num(a[0]) != 0.0
                    val r = if (cond) a[1] else if (a.size > 2) a[2] else V.Num(0.0)
                    if (r is V.Rng) throw SheetError("#VALUE")
                    r
                }
                "SUMIF" -> {
                    need(2)
                    val rr = refs(a[0])
                    val sr = if (a.size > 2) refs(a[2]) else rr
                    val m = matcher(a[1])
                    var total = 0.0
                    for (k in 0 until minOf(rr.size, sr.size)) {
                        if (m(value(rr[k]))) {
                            val sv = value(sr[k])
                            if (sv is V.Num) total += sv.d else if (sv is V.Err) throw SheetError(sv.m)
                        }
                    }
                    V.Num(total)
                }
                "COUNTIF" -> {
                    need(2)
                    val m = matcher(a[1])
                    V.Num(refs(a[0]).count { m(value(it)) }.toDouble())
                }
                else -> throw SheetError("#NAME")
            }
        }
    }
}
