package com.flightwatch.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One recorded price point. */
data class PricePoint(
    val id: Long,
    val flightKey: String,
    val airline: String,
    val flightNumber: String,
    val departureTime: String,
    val arrivalTime: String,
    val returnFlightNumber: String,
    val returnDepartureTime: String,
    val returnArrivalTime: String,
    val origin: String,
    val destination: String,
    val price: Double,
    val currency: String,
    val isOfficial: Boolean,
    val seller: String,
    val recordedAt: Long,
)

/**
 * Price history, stored in a plain SQLite table.
 *
 * Every observed price change is appended here - not just the current price -
 * so the History page can show how a fare moved over time.
 */
class HistoryStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                flight_key TEXT NOT NULL,
                airline TEXT,
                flight_number TEXT,
                departure_time TEXT,
                arrival_time TEXT,
                return_flight_number TEXT,
                return_departure_time TEXT,
                return_arrival_time TEXT,
                origin TEXT,
                destination TEXT,
                price REAL NOT NULL,
                currency TEXT,
                is_official INTEGER NOT NULL DEFAULT 0,
                seller TEXT,
                recorded_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_flight ON $TABLE(flight_key, recorded_at)")
        db.execSQL("CREATE INDEX idx_history_time ON $TABLE(recorded_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive migration - keep existing price history.
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN arrival_time TEXT")
            } catch (e: Exception) {
                // Column already there.
            }
        }
        if (oldVersion < 3) {
            for (col in listOf(
                "return_flight_number TEXT",
                "return_departure_time TEXT",
                "return_arrival_time TEXT",
            )) {
                try {
                    db.execSQL("ALTER TABLE $TABLE ADD COLUMN $col")
                } catch (e: Exception) {
                    // Column already there.
                }
            }
        }
    }

    /**
     * Identity of one complete round trip: outbound flight + return flight.
     *
     * Everything that makes a ticket unique goes in - route, both flight
     * numbers, both departure and arrival times - so two different itineraries
     * never share a price chart, while the same itinerary seen on different
     * days lands in one.
     */
    fun ticketKey(
        origin: String,
        destination: String,
        outboundKey: String,
        outboundArrival: String,
        returnFlightNumber: String,
        returnDeparture: String,
        returnArrival: String,
    ): String = listOf(
        origin, destination,
        outboundKey, outboundArrival,
        returnFlightNumber, returnDeparture, returnArrival,
    ).joinToString("|")

    /** Append a price point. Returns true when the price actually changed. */
    fun record(
        flightKey: String,
        airline: String,
        flightNumber: String,
        departureTime: String,
        arrivalTime: String = "",
        returnFlightNumber: String = "",
        returnDepartureTime: String = "",
        returnArrivalTime: String = "",
        origin: String,
        destination: String,
        price: Double,
        currency: String,
        isOfficial: Boolean,
        seller: String,
        at: Long = System.currentTimeMillis(),
    ): Boolean {
        if (price <= 0.0) return false
        val last = latestFor(flightKey, isOfficial)
        if (last != null && last.price == price) return false
        val values = ContentValues().apply {
            put("flight_key", flightKey)
            put("airline", airline)
            put("flight_number", flightNumber)
            put("departure_time", departureTime)
            put("arrival_time", arrivalTime)
            put("return_flight_number", returnFlightNumber)
            put("return_departure_time", returnDepartureTime)
            put("return_arrival_time", returnArrivalTime)
            put("origin", origin)
            put("destination", destination)
            put("price", price)
            put("currency", currency)
            put("is_official", if (isOfficial) 1 else 0)
            put("seller", seller)
            put("recorded_at", at)
        }
        writableDatabase.insert(TABLE, null, values)
        return true
    }

    fun latestFor(flightKey: String, officialOnly: Boolean): PricePoint? {
        val sql = StringBuilder(
            "SELECT * FROM $TABLE WHERE flight_key = ?"
        )
        val args = arrayListOf(flightKey)
        if (officialOnly) sql.append(" AND is_official = 1")
        sql.append(" ORDER BY recorded_at DESC LIMIT 1")
        return readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
            if (c.moveToFirst()) readRow(c) else null
        }
    }

    /** Every recorded price change, newest first. */
    fun all(limit: Int = 500): List<PricePoint> =
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE ORDER BY recorded_at DESC LIMIT $limit",
            null,
        ).use { c ->
            val out = mutableListOf<PricePoint>()
            while (c.moveToNext()) out += readRow(c)
            out
        }

    /** History for one flight, oldest first - used for the sparkline. */
    fun forFlight(flightKey: String, limit: Int = 100): List<PricePoint> =
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE WHERE flight_key = ? ORDER BY recorded_at ASC LIMIT $limit",
            arrayOf(flightKey),
        ).use { c ->
            val out = mutableListOf<PricePoint>()
            while (c.moveToNext()) out += readRow(c)
            out
        }

    fun distinctFlights(): List<String> =
        readableDatabase.rawQuery(
            "SELECT flight_key FROM $TABLE GROUP BY flight_key ORDER BY MAX(recorded_at) DESC",
            null,
        ).use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) out += c.getString(0)
            out
        }

    fun clear() = writableDatabase.delete(TABLE, null, null)

    private fun readRow(c: android.database.Cursor): PricePoint = PricePoint(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        flightKey = c.getString(c.getColumnIndexOrThrow("flight_key")),
        airline = c.getString(c.getColumnIndexOrThrow("airline")) ?: "",
        flightNumber = c.getString(c.getColumnIndexOrThrow("flight_number")) ?: "",
        departureTime = c.getString(c.getColumnIndexOrThrow("departure_time")),
        arrivalTime = run {
            val i = c.getColumnIndex("arrival_time")
            if (i >= 0 && !c.isNull(i)) c.getString(i) else ""
        },
        returnFlightNumber = c.getStringOrNull("return_flight_number"),
        returnDepartureTime = c.getStringOrNull("return_departure_time"),
        returnArrivalTime = c.getStringOrNull("return_arrival_time"),
        origin = c.getString(c.getColumnIndexOrThrow("origin")),
        destination = c.getString(c.getColumnIndexOrThrow("destination")),
        price = c.getDouble(c.getColumnIndexOrThrow("price")),
        currency = c.getString(c.getColumnIndexOrThrow("currency")) ?: "",
        isOfficial = c.getInt(c.getColumnIndexOrThrow("is_official")) == 1,
        seller = c.getString(c.getColumnIndexOrThrow("seller")) ?: "",
        recordedAt = c.getLong(c.getColumnIndexOrThrow("recorded_at")),
    )

    companion object {
        private const val DB_NAME = "flight_watch.db"
        private const val DB_VERSION = 3
        private const val TABLE = "price_history"
    }
}

/** Read a column that may be missing (pre-migration rows) or NULL. */
private fun android.database.Cursor.getStringOrNull(column: String): String {
    val i = getColumnIndex(column)
    return if (i >= 0 && !isNull(i)) getString(i) else ""
}
