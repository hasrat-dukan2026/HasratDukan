package af.hasrat.dukan

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var root: LinearLayout
    private val scan = registerForActivityResult(ScanContract()) { r -> if(r.contents!=null) showProduct(r.contents) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); db=Db(this); if(db.setting("password")==null) setupPassword() else login() }
    private fun base(title:String) {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18); layoutDirection=android.view.View.LAYOUT_DIRECTION_RTL }
        val sc=ScrollView(this); sc.addView(root); setContentView(sc)
        root.addView(TextView(this).apply { text=title; textSize=25f;gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;setPadding(0,12,0,20) })
    }
    private fun field(hint:String, value:String=""): EditText = EditText(this).apply { this.hint=hint;setText(value);textSize=17f }
    private fun btn(label:String, action:()->Unit) { root.addView(Button(this).apply { text=label;textSize=17f;setOnClickListener{action()} }) }
    private fun setupPassword() {
        base("حسرت دوکان — لومړی پاسورډ جوړ کړه")
        val p=field("نوی پاسورډ"); root.addView(p)
        btn("پاسورډ خوندي کړه") { if(p.text.length<4) msg("پاسورډ لږ تر لږه 4 توري وي") else { db.setSetting("password",p.text.toString()); home() } }
    }
    private fun login() {
        base("حسرت دوکان")
        val p=field("پاسورډ"); root.addView(p)
        btn("ننوتل") { if(p.text.toString()==db.setting("password")) home() else msg("پاسورډ ناسم دی") }
    }
    private fun home() {
        base("حسرت دوکان")
        btn("د مالونو نوملړ") { listProducts() }
        btn("نوی مال ثبتول") { addProduct() }
        btn("بارکوډ سکین کول") { val o=ScanOptions().setPrompt("د مال بارکوډ سکین کړه").setBeepEnabled(false).setOrientationLocked(false);scan.launch(o) }
        btn("پېرود ثبتول") { transaction("پېرود") }
        btn("خرڅلاو ثبتول") { transaction("خرڅلاو") }
        btn("بېرته اخیستل") { transaction("بېرته اخیستل") }
        btn("پور ثبتول") { addDebt() }
        btn("پورونه") { show("د مشتریانو پورونه",db.debts()) }
        btn("ورځنی لګښت") { addExpense() }
        btn("ګدامونه او راپورونه") { warehouses() }
        btn("عمومي مالي راپور") { show("عمومي راپور",db.summary()) }
        btn("پاسورډ بدلول") { changePassword() }
    }
    private fun addProduct() {
        base("نوی مال ثبتول")
        val n=field("د مال نوم"); val code=field("ځانګړی کوډ"); val bar=field("بارکوډ (اختیاري)"); val w=field("ګدام نوم");val q=field("ابتدایي تعداد","0");val c=field("اصلي قیمت","0");val p=field("د خرڅلاو قیمت","0");val s=field("عرضه کوونکی")
        listOf(n,code,bar,w,q,c,p,s).forEach{root.addView(it)}
        btn("مال خوندي کړه") { try { db.addProduct(n.text.toString(),code.text.toString(),bar.text.toString(),w.text.toString().ifBlank{"د نورو مالونو ګدام"},q.text.toString().toDouble(),c.text.toString().toDouble(),p.text.toString().toDouble(),s.text.toString());msg("مال ثبت شو");home() } catch(e:Exception){msg("ستونزه: ${e.message}")} }
    }
    private fun transaction(kind:String) {
        base("$kind ثبتول")
        val code=field("د مال کوډ یا بارکوډ");val qty=field("تعداد","1");val cost=field("د واحد قیمت","0");val who=field("مشتری یا عرضه کوونکی")
        listOf(code,qty,cost,who).forEach{root.addView(it)}
        btn("ثبتول") { try { db.transact(code.text.toString(),kind,qty.text.toString().toDouble(),cost.text.toString().toDouble(),who.text.toString());msg("$kind ثبت شو");home() }catch(e:Exception){msg("ستونزه: ${e.message}")} }
        btn("بارکوډ سکین") { scan.launch(ScanOptions().setPrompt("بارکوډ سکین کړه").setBeepEnabled(false)) }
    }
    private fun addDebt() {
        base("د مشتری پور")
        val n=field("د مشتری نوم");val a=field("د پور اندازه","0");val p=field("ورکړل شوې پیسې","0")
        listOf(n,a,p).forEach{root.addView(it)}
        btn("پور خوندي کړه") { try{db.addDebt(n.text.toString(),a.text.toString().toDouble(),p.text.toString().toDouble());msg("پور ثبت شو");home()}catch(e:Exception){msg("معلومات سم ولیکه")} }
    }
    private fun addExpense() {
        base("ورځنی لګښت")
        val n=field("د لګښت نوم");val a=field("اندازه AFN","0");root.addView(n);root.addView(a)
        btn("لګښت ثبت کړه") { try{db.addExpense(n.text.toString(),a.text.toString().toDouble());msg("لګښت ثبت شو");home()}catch(e:Exception){msg("اندازه سمه ولیکه")} }
    }
    private fun listProducts() { base("د مالونو نوملړ"); db.products().forEach{root.addView(TextView(this).apply{text=it;textSize=16f;setPadding(4,12,4,12)})};btn("شاته"){home()} }
    private fun warehouses() {
        base("جلا ګدامونه")
        listOf("د ګوشکو ګدام","د کیبلونو ګدام","د چارجرونو ګدام","د موبایلونو ګدام","د ساعتونو ګدام","د سولر ګروپونو ګدام","د بیټریو ګدام","د نورو مالونو ګدام").forEach{w->btn(w){show(w,db.warehouseReport(w))}}
        btn("شاته"){home()}
    }
    private fun showProduct(code:String) = show("د مال لټون",db.productByCode(code))
    private fun show(t:String,s:String) { AlertDialog.Builder(this).setTitle(t).setMessage(s).setPositiveButton("سمه ده",null).show() }
    private fun changePassword() { val p=field("نوی پاسورډ");AlertDialog.Builder(this).setTitle("پاسورډ بدلول").setView(p).setPositiveButton("خوندي"){_,_->if(p.text.length>=4)db.setSetting("password",p.text.toString())}.setNegativeButton("لغوه",null).show() }
    private fun msg(s:String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
