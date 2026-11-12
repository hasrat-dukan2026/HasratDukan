package af.hasrat.dukan

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(c: Context) : SQLiteOpenHelper(c, "hasrat_dukan.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY, name TEXT NOT NULL, code TEXT UNIQUE, barcode TEXT, warehouse TEXT, qty REAL DEFAULT 0, cost REAL DEFAULT 0, price REAL DEFAULT 0, bought TEXT, supplier TEXT)")
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY, product_id INTEGER, kind TEXT, qty REAL, unit_cost REAL, total REAL, person TEXT, date TEXT)")
        db.execSQL("CREATE TABLE debts(id INTEGER PRIMARY KEY, customer TEXT, amount REAL, paid REAL DEFAULT 0, date TEXT)")
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY, title TEXT, amount REAL, date TEXT)")
        db.execSQL("CREATE TABLE cash(id INTEGER PRIMARY KEY, kind TEXT, amount REAL, note TEXT, date TEXT)")
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
        val names = listOf("ایلفی","آب ګرمې","ټاپسی کیبل","سمسینګ کیبل","آیفون کیبل","ساعتونه","مبایلونه","سولر ګروپونه","چارجر سمسینګ","چارجر ټاپسی","بیټری چارجر","هلال بیټری","0100 لین","کټوخه","بټن","ولډر","ذربه","ګاټونه","ساکیټی","ساکیټ","موټر چارجر","موټر MP3","فلش نوکه","فیوز","شڼټیپ","بیجلی","نوکی","ریسور")
        names.forEachIndexed { i, n ->
            val v = ContentValues().apply { put("name", n); put("code", "H%03d".format(i+1)); put("warehouse", warehouseFor(n)) }
            db.insert("products", null, v)
        }
        listOf("د ګوشکو ګدام","د کیبلونو ګدام","د چارجرونو ګدام","د موبایلونو ګدام","د ساعتونو ګدام","د سولر ګروپونو ګدام","د بیټریو ګدام","د نورو مالونو ګدام").forEach { w ->
            db.execSQL("INSERT OR IGNORE INTO settings(k,v) VALUES(?,?)", arrayOf("warehouse:$w", w))
        }
    }
    private fun warehouseFor(n: String): String = when {
        n.contains("کیبل") -> "د کیبلونو ګدام"
        n.contains("چارجر") -> "د چارجرونو ګدام"
        n.contains("مبایل") -> "د موبایلونو ګدام"
        n.contains("ساعت") -> "د ساعتونو ګدام"
        n.contains("سولر") -> "د سولر ګروپونو ګدام"
        n.contains("بیټری") -> "د بیټریو ګدام"
        n.contains("ګوش") -> "د ګوشکو ګدام"
        else -> "د نورو مالونو ګدام"
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun setting(k: String): String? = readableDatabase.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun setSetting(k: String, v: String) { writableDatabase.execSQL("INSERT OR REPLACE INTO settings(k,v) VALUES(?,?)", arrayOf(k,v)) }
    fun addProduct(name:String, code:String, barcode:String, warehouse:String, qty:Double, cost:Double, price:Double, supplier:String) {
        val cv=ContentValues().apply { put("name",name);put("code",code);put("barcode",barcode);put("warehouse",warehouse);put("qty",qty);put("cost",cost);put("price",price);put("supplier",supplier) }
        writableDatabase.insertOrThrow("products",null,cv)
    }
    fun productByCode(code:String) = readableDatabase.rawQuery("SELECT id,name,qty,cost,price,warehouse FROM products WHERE code=? OR barcode=?", arrayOf(code,code)).use { if(it.moveToFirst()) "${it.getString(1)} | موجودي: ${it.getDouble(2)}" else "مال پیدا نه شو" }
    fun transact(code:String, kind:String, qty:Double, unitCost:Double, person:String="") {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val c=db.rawQuery("SELECT id,qty,cost,price FROM products WHERE code=? OR barcode=?", arrayOf(code,code))
            if(!c.moveToFirst()) throw Exception("مال پیدا نه شو")
            val id=c.getLong(0); val old=c.getDouble(1); val cost=c.getDouble(2); val price=c.getDouble(3); c.close()
            val newQty=when(kind) { "پېرود" -> old+qty; "خرڅلاو" -> old-qty; "بېرته اخیستل" -> old+qty; else -> old }
            if(newQty < 0) throw Exception("موجودي کافي نه ده")
            val cv=ContentValues().apply { put("qty",newQty); if(kind=="پېرود") put("cost",unitCost) }
            db.update("products",cv,"id=?", arrayOf(id.toString()))
            val v=ContentValues().apply { put("product_id",id);put("kind",kind);put("qty",qty);put("unit_cost",if(kind=="خرڅلاو") cost else unitCost);put("total",qty*(if(kind=="خرڅلاو") price else unitCost));put("person",person);put("date",java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US).format(java.util.Date())) }
            db.insert("movements",null,v); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun products(): List<String> {
        val out= mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name,code,qty,cost,price,warehouse FROM products ORDER BY name",null).use { c ->
            while(c.moveToNext()) out.add("${c.getString(0)} (${c.getString(1)})\nموجودي: ${c.getDouble(2)} | اصلي: ${c.getDouble(3)} AFN | خرڅلاو: ${c.getDouble(4)} AFN\n${c.getString(5)}")
        }; return out
    }
    fun summary(): String {
        val db=readableDatabase
        val stock=db.rawQuery("SELECT COALESCE(SUM(qty*cost),0), COALESCE(SUM(qty),0) FROM products",null).use { it.moveToFirst(); "د مالونو اصلي ارزښت: ${it.getDouble(0)} AFN\nټول تعداد: ${it.getDouble(1)}" }
        val sales=db.rawQuery("SELECT COALESCE(SUM(total),0) FROM movements WHERE kind='خرڅلاو'",null).use { it.moveToFirst();it.getDouble(0) }
        val profit=db.rawQuery("SELECT COALESCE(SUM(total-qty*unit_cost),0) FROM movements WHERE kind='خرڅلاو'",null).use { it.moveToFirst(); it.getDouble(0) }
        val debt=db.rawQuery("SELECT COALESCE(SUM(amount-paid),0) FROM debts",null).use { it.moveToFirst();it.getDouble(0) }
        val expense=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM expenses",null).use { it.moveToFirst();it.getDouble(0) }
        return "$stock\nټول خرڅلاو: $sales AFN\nپاتې پورونه: $debt AFN\nلګښتونه: $expense AFN"
    }
    fun addDebt(name:String, amount:Double, paid:Double) { val v=ContentValues().apply{put("customer",name);put("amount",amount);put("paid",paid);put("date",today())};writableDatabase.insert("debts",null,v) }
    fun addExpense(name:String, amount:Double) { val v=ContentValues().apply{put("title",name);put("amount",amount);put("date",today())};writableDatabase.insert("expenses",null,v) }
    fun debts():String = readableDatabase.rawQuery("SELECT customer,amount,paid FROM debts ORDER BY id DESC",null).use { c-> val s=StringBuilder();while(c.moveToNext())s.append("${c.getString(0)}: پور ${c.getDouble(1)}، ورکړل ${c.getDouble(2)}، پاتې ${c.getDouble(1)-c.getDouble(2)} AFN\n");s.toString().ifBlank{"پور نشته"} }
    fun warehouseReport(w:String):String = readableDatabase.rawQuery("SELECT name,qty,cost,price FROM products WHERE warehouse=?", arrayOf(w)).use { c-> val s=StringBuilder("$w\n");while(c.moveToNext())s.append("${c.getString(0)} — ${c.getDouble(1)} عدد\n");s.toString() }
    companion object { fun today()=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date()) }
}
