package com.avicp2023.budgetmanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BudgetDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "budget_manager.db";
    private static final int DB_VERSION = 1;

    public BudgetDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, icon TEXT NOT NULL, type TEXT NOT NULL, monthly_budget INTEGER NOT NULL DEFAULT 0, yearly_budget INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE transactions (_id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount INTEGER NOT NULL, category_id INTEGER, category_name TEXT NOT NULL, category_icon TEXT NOT NULL, note TEXT, date TEXT NOT NULL, payment TEXT, recurring_id INTEGER NOT NULL DEFAULT 0, recurring_due TEXT)");
        db.execSQL("CREATE UNIQUE INDEX idx_recurring_once ON transactions(recurring_id, recurring_due)");
        db.execSQL("CREATE TABLE recurring (_id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount INTEGER NOT NULL, category_id INTEGER, category_name TEXT NOT NULL, category_icon TEXT NOT NULL, note TEXT, frequency TEXT NOT NULL, next_due TEXT NOT NULL, end_date TEXT, active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        seed(db);
        putSetting(db, "opening_balance", "0");
        putSetting(db, "monthly_budget", "0");
        putSetting(db, "yearly_budget", "0");
    }

    private void seed(SQLiteDatabase db) {
        addSeed(db, "משכורת", "💼", "INCOME");
        addSeed(db, "הכנסה נוספת", "💰", "INCOME");
        addSeed(db, "מתנות", "🎁", "INCOME");
        addSeed(db, "מזון וקניות", "🛒", "EXPENSE");
        addSeed(db, "דיור", "🏠", "EXPENSE");
        addSeed(db, "חשבונות", "💡", "EXPENSE");
        addSeed(db, "תחבורה", "🚗", "EXPENSE");
        addSeed(db, "בריאות", "🩺", "EXPENSE");
        addSeed(db, "ביגוד", "👕", "EXPENSE");
        addSeed(db, "בילויים", "🎬", "EXPENSE");
        addSeed(db, "חינוך", "📚", "EXPENSE");
        addSeed(db, "אחר", "📦", "EXPENSE");
    }

    private void addSeed(SQLiteDatabase db, String name, String icon, String type) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("icon", icon); v.put("type", type);
        db.insert("categories", null, v);
    }

    private void putSetting(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues(); v.put("key", key); v.put("value", value);
        db.insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public String getSetting(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }

    public void setSetting(String key, String value) { putSetting(getWritableDatabase(), key, value); }

    public List<Category> categories(String type) {
        List<Category> out = new ArrayList<>();
        String sql = "ALL".equals(type) ? "SELECT * FROM categories ORDER BY type, name" : "SELECT * FROM categories WHERE type=? ORDER BY name";
        try (Cursor c = "ALL".equals(type) ? getReadableDatabase().rawQuery(sql, null) : getReadableDatabase().rawQuery(sql, new String[]{type})) {
            while (c.moveToNext()) out.add(new Category(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("icon")), c.getString(c.getColumnIndexOrThrow("type")), c.getLong(c.getColumnIndexOrThrow("monthly_budget")), c.getLong(c.getColumnIndexOrThrow("yearly_budget"))));
        }
        return out;
    }

    public long addCategory(String name, String icon, String type, long monthly, long yearly) {
        ContentValues v = new ContentValues(); v.put("name", name); v.put("icon", icon); v.put("type", type); v.put("monthly_budget", monthly); v.put("yearly_budget", yearly);
        return getWritableDatabase().insert("categories", null, v);
    }

    public void updateCategory(long id, String name, String icon, String type, long monthly, long yearly) {
        ContentValues v = new ContentValues(); v.put("name", name); v.put("icon", icon); v.put("type", type); v.put("monthly_budget", monthly); v.put("yearly_budget", yearly);
        getWritableDatabase().update("categories", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteCategory(long id) { getWritableDatabase().delete("categories", "_id=?", new String[]{String.valueOf(id)}); }

    public long addTransaction(String type, long amount, Category category, String note, String date, String payment) {
        return addTransaction(type, amount, category.id, category.name, category.icon, note, date, payment, 0, null);
    }

    private long addTransaction(String type, long amount, long categoryId, String categoryName, String categoryIcon, String note, String date, String payment, long recurringId, String recurringDue) {
        ContentValues v = new ContentValues();
        v.put("type", type); v.put("amount", amount); v.put("category_id", categoryId);
        v.put("category_name", categoryName); v.put("category_icon", categoryIcon);
        v.put("note", note); v.put("date", date); v.put("payment", payment);
        v.put("recurring_id", recurringId); v.put("recurring_due", recurringDue);
        return getWritableDatabase().insertWithOnConflict("transactions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateTransaction(long id, String type, long amount, Category category, String note, String date, String payment) {
        ContentValues v = new ContentValues(); v.put("type", type); v.put("amount", amount); v.put("category_id", category.id);
        v.put("category_name", category.name); v.put("category_icon", category.icon); v.put("note", note); v.put("date", date); v.put("payment", payment);
        getWritableDatabase().update("transactions", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteTransaction(long id) { getWritableDatabase().delete("transactions", "_id=?", new String[]{String.valueOf(id)}); }

    public List<TransactionRecord> transactions(String query, String filter) {
        List<TransactionRecord> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (!"ALL".equals(filter)) { sql.append(" AND type=?"); args.add(filter); }
        if (query != null && !query.trim().isEmpty()) { sql.append(" AND (note LIKE ? OR category_name LIKE ? OR date LIKE ?)"); String q = "%" + query.trim() + "%"; args.add(q); args.add(q); args.add(q); }
        sql.append(" ORDER BY date DESC, _id DESC");
        try (Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) out.add(readTransaction(c));
        }
        return out;
    }

    public List<TransactionRecord> recentTransactions(int limit) {
        List<TransactionRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM transactions ORDER BY date DESC, _id DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(readTransaction(c));
        }
        return out;
    }

    private TransactionRecord readTransaction(Cursor c) {
        return new TransactionRecord(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("type")), c.getLong(c.getColumnIndexOrThrow("amount")), c.getLong(c.getColumnIndexOrThrow("category_id")), c.getString(c.getColumnIndexOrThrow("category_name")), c.getString(c.getColumnIndexOrThrow("category_icon")), c.getString(c.getColumnIndexOrThrow("note")), c.getString(c.getColumnIndexOrThrow("date")), c.getString(c.getColumnIndexOrThrow("payment")));
    }

    public long sum(String type, String start, String end) {
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type=?");
        List<String> args = new ArrayList<>(); args.add(type);
        if (start != null) { sql.append(" AND date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND date<=?"); args.add(end); }
        try (Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]))) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    public long balance() {
        long opening = 0;
        try { opening = Long.parseLong(getSetting("opening_balance", "0")); } catch (Exception ignored) { }
        return opening + sum("INCOME", null, null) - sum("EXPENSE", null, null);
    }

    public List<CategoryTotal> categoryTotals(String start, String end, String type) {
        List<CategoryTotal> out = new ArrayList<>();
        String sql = "SELECT category_name, category_icon, SUM(amount) AS total FROM transactions WHERE type=? AND date>=? AND date<=? GROUP BY category_name, category_icon ORDER BY total DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{type, start, end})) {
            while (c.moveToNext()) out.add(new CategoryTotal(c.getString(0), c.getString(1), c.getLong(2)));
        }
        return out;
    }

    public long addRecurring(String type, long amount, Category category, String note, String frequency, String nextDue, String endDate) {
        ContentValues v = new ContentValues(); v.put("type", type); v.put("amount", amount); v.put("category_id", category.id);
        v.put("category_name", category.name); v.put("category_icon", category.icon); v.put("note", note);
        v.put("frequency", frequency); v.put("next_due", nextDue); v.put("end_date", endDate); v.put("active", 1);
        return getWritableDatabase().insert("recurring", null, v);
    }

    public List<RecurringRule> recurringRules() {
        List<RecurringRule> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM recurring ORDER BY active DESC, next_due", null)) {
            while (c.moveToNext()) out.add(new RecurringRule(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("type")), c.getLong(c.getColumnIndexOrThrow("amount")), c.getLong(c.getColumnIndexOrThrow("category_id")), c.getString(c.getColumnIndexOrThrow("category_name")), c.getString(c.getColumnIndexOrThrow("category_icon")), c.getString(c.getColumnIndexOrThrow("note")), c.getString(c.getColumnIndexOrThrow("frequency")), c.getString(c.getColumnIndexOrThrow("next_due")), c.getString(c.getColumnIndexOrThrow("end_date")), c.getInt(c.getColumnIndexOrThrow("active")) == 1));
        }
        return out;
    }

    public void toggleRecurring(long id, boolean active) {
        ContentValues v = new ContentValues(); v.put("active", active ? 1 : 0);
        getWritableDatabase().update("recurring", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteRecurring(long id) { getWritableDatabase().delete("recurring", "_id=?", new String[]{String.valueOf(id)}); }

    public void processRecurring() {
        SQLiteDatabase db = getWritableDatabase();
        LocalDate today = LocalDate.now();
        db.beginTransaction();
        try (Cursor c = db.rawQuery("SELECT * FROM recurring WHERE active=1", null)) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("_id"));
                String type = c.getString(c.getColumnIndexOrThrow("type"));
                long amount = c.getLong(c.getColumnIndexOrThrow("amount"));
                long categoryId = c.getLong(c.getColumnIndexOrThrow("category_id"));
                String categoryName = c.getString(c.getColumnIndexOrThrow("category_name"));
                String categoryIcon = c.getString(c.getColumnIndexOrThrow("category_icon"));
                String note = c.getString(c.getColumnIndexOrThrow("note"));
                String frequency = c.getString(c.getColumnIndexOrThrow("frequency"));
                String next = c.getString(c.getColumnIndexOrThrow("next_due"));
                String end = c.getString(c.getColumnIndexOrThrow("end_date"));
                LocalDate due = LocalDate.parse(next);
                while (!due.isAfter(today) && (end == null || end.isEmpty() || !due.isAfter(LocalDate.parse(end)))) {
                    addTransaction(type, amount, categoryId, categoryName, categoryIcon, note, due.toString(), "הוראה קבועה", id, due.toString());
                    due = nextDate(due, frequency);
                }
                ContentValues v = new ContentValues();
                v.put("next_due", due.toString());
                if (end != null && !end.isEmpty() && due.isAfter(LocalDate.parse(end))) v.put("active", 0);
                db.update("recurring", v, "_id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally { db.endTransaction(); }
    }

    private LocalDate nextDate(LocalDate date, String frequency) {
        switch (frequency) {
            case "DAILY": return date.plusDays(1);
            case "WEEKLY": return date.plusWeeks(1);
            case "BIMONTHLY": return date.plusMonths(2);
            case "YEARLY": return date.plusYears(1);
            default: return date.plusMonths(1);
        }
    }

    public String exportJson() throws Exception {
        JSONObject root = new JSONObject(); root.put("format", "budget-manager-backup-v1");
        JSONArray cats = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM categories", null)) {
            while (c.moveToNext()) { JSONObject o = new JSONObject(); o.put("id", c.getLong(0)); o.put("name", c.getString(1)); o.put("icon", c.getString(2)); o.put("type", c.getString(3)); o.put("monthly_budget", c.getLong(4)); o.put("yearly_budget", c.getLong(5)); cats.put(o); }
        }
        JSONArray txs = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM transactions", null)) {
            while (c.moveToNext()) { JSONObject o = new JSONObject(); o.put("id", c.getLong(0)); o.put("type", c.getString(1)); o.put("amount", c.getLong(2)); o.put("category_id", c.getLong(3)); o.put("category_name", c.getString(4)); o.put("category_icon", c.getString(5)); o.put("note", c.getString(6)); o.put("date", c.getString(7)); o.put("payment", c.getString(8)); o.put("recurring_id", c.getLong(9)); o.put("recurring_due", c.isNull(10) ? JSONObject.NULL : c.getString(10)); txs.put(o); }
        }
        JSONArray rules = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM recurring", null)) {
            while (c.moveToNext()) { JSONObject o = new JSONObject(); o.put("id", c.getLong(0)); o.put("type", c.getString(1)); o.put("amount", c.getLong(2)); o.put("category_id", c.getLong(3)); o.put("category_name", c.getString(4)); o.put("category_icon", c.getString(5)); o.put("note", c.getString(6)); o.put("frequency", c.getString(7)); o.put("next_due", c.getString(8)); o.put("end_date", c.isNull(9) ? JSONObject.NULL : c.getString(9)); o.put("active", c.getInt(10)); rules.put(o); }
        }
        JSONObject settings = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT key,value FROM settings", null)) { while (c.moveToNext()) settings.put(c.getString(0), c.getString(1)); }
        root.put("categories", cats); root.put("transactions", txs); root.put("recurring", rules); root.put("settings", settings);
        return root.toString(2);
    }

    public void importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"budget-manager-backup-v1".equals(root.optString("format"))) throw new Exception("קובץ הגיבוי אינו מזוהה");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("transactions", null, null); db.delete("categories", null, null); db.delete("recurring", null, null); db.delete("settings", null, null);
            JSONArray cats = root.getJSONArray("categories");
            for (int i=0;i<cats.length();i++) { JSONObject o=cats.getJSONObject(i); ContentValues v=new ContentValues(); v.put("_id",o.getLong("id")); v.put("name",o.getString("name")); v.put("icon",o.getString("icon")); v.put("type",o.getString("type")); v.put("monthly_budget",o.optLong("monthly_budget")); v.put("yearly_budget",o.optLong("yearly_budget")); db.insert("categories",null,v); }
            JSONArray txs = root.getJSONArray("transactions");
            for (int i=0;i<txs.length();i++) { JSONObject o=txs.getJSONObject(i); ContentValues v=new ContentValues(); v.put("_id",o.getLong("id")); v.put("type",o.getString("type")); v.put("amount",o.getLong("amount")); v.put("category_id",o.optLong("category_id")); v.put("category_name",o.getString("category_name")); v.put("category_icon",o.getString("category_icon")); v.put("note",o.optString("note")); v.put("date",o.getString("date")); v.put("payment",o.optString("payment")); v.put("recurring_id",o.optLong("recurring_id")); if (o.isNull("recurring_due")) v.putNull("recurring_due"); else v.put("recurring_due",o.getString("recurring_due")); db.insert("transactions",null,v); }
            JSONArray rules = root.optJSONArray("recurring");
            if (rules != null) for (int i=0;i<rules.length();i++) { JSONObject o=rules.getJSONObject(i); ContentValues v=new ContentValues(); v.put("_id",o.getLong("id")); v.put("type",o.getString("type")); v.put("amount",o.getLong("amount")); v.put("category_id",o.optLong("category_id")); v.put("category_name",o.getString("category_name")); v.put("category_icon",o.getString("category_icon")); v.put("note",o.optString("note")); v.put("frequency",o.getString("frequency")); v.put("next_due",o.getString("next_due")); if(o.isNull("end_date"))v.putNull("end_date");else v.put("end_date",o.getString("end_date")); v.put("active",o.optInt("active",1)); db.insert("recurring",null,v); }
            JSONObject settings = root.optJSONObject("settings");
            if (settings != null) { JSONArray keys = settings.names(); if (keys != null) for(int i=0;i<keys.length();i++){String k=keys.getString(i); putSetting(db,k,settings.getString(k));} }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public String exportCsv() {
        StringBuilder out = new StringBuilder("\uFEFFתאריך,סוג,סכום בשקלים,קטגוריה,תיאור,אמצעי תשלום\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT date,type,amount,category_name,note,payment FROM transactions ORDER BY date,_id", null)) {
            while (c.moveToNext()) {
                out.append(csv(c.getString(0))).append(',').append(csv("INCOME".equals(c.getString(1)) ? "הכנסה" : "הוצאה")).append(',');
                out.append(String.format(java.util.Locale.US, "%.2f", c.getLong(2) / 100.0)).append(',');
                out.append(csv(c.getString(3))).append(',').append(csv(c.getString(4))).append(',').append(csv(c.getString(5))).append('\n');
            }
        }
        return out.toString();
    }

    private String csv(String s) { if (s == null) s = ""; return "\"" + s.replace("\"", "\"\"") + "\""; }

    public static class Category {
        public long id, monthlyBudget, yearlyBudget; public String name, icon, type;
        public Category(long id,String name,String icon,String type,long monthly,long yearly){this.id=id;this.name=name;this.icon=icon;this.type=type;this.monthlyBudget=monthly;this.yearlyBudget=yearly;}
        @Override public String toString(){return icon+" "+name;}
    }
    public static class TransactionRecord {
        public long id, amount, categoryId; public String type, categoryName, categoryIcon, note, date, payment;
        public TransactionRecord(long id,String type,long amount,long categoryId,String name,String icon,String note,String date,String payment){this.id=id;this.type=type;this.amount=amount;this.categoryId=categoryId;this.categoryName=name;this.categoryIcon=icon;this.note=note;this.date=date;this.payment=payment;}
    }
    public static class CategoryTotal { public String name,icon; public long total; public CategoryTotal(String n,String i,long t){name=n;icon=i;total=t;} }
    public static class RecurringRule {
        public long id,amount,categoryId; public String type,categoryName,categoryIcon,note,frequency,nextDue,endDate; public boolean active;
        public RecurringRule(long id,String type,long amount,long categoryId,String name,String icon,String note,String frequency,String nextDue,String endDate,boolean active){this.id=id;this.type=type;this.amount=amount;this.categoryId=categoryId;this.categoryName=name;this.categoryIcon=icon;this.note=note;this.frequency=frequency;this.nextDue=nextDue;this.endDate=endDate;this.active=active;}
    }
}
