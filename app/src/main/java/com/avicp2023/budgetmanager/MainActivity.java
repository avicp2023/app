package com.avicp2023.budgetmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private BudgetDb db;
    private LinearLayout root, pageHost, bottomBar;
    private int currentTab = 0;
    private static final int REQ_BACKUP = 7101, REQ_CSV = 7102, REQ_RESTORE = 7103;
    private final String[] emojiOptions = {"🛒","🏠","💡","🚗","🩺","👕","🎬","📚","💼","💰","🎁","📦","🐾","✈️","⚽","📱","🧾","🎓","🏦","🛠️"};
    private final String[] paymentOptions = {"מזומן","כרטיס אשראי","העברה בנקאית","הוראת קבע","אחר"};
    private final String[] frequencyLabels = {"חודשי","שבועי","אחת לחודשיים","שנתי","יומי"};
    private final String[] frequencyCodes = {"MONTHLY","WEEKLY","BIMONTHLY","YEARLY","DAILY"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(15, 48, 63));
        getWindow().setNavigationBarColor(Color.rgb(255,255,255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        db = new BudgetDb(this);
        db.processRecurring();
        buildShell();
        render();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(245,247,250));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackgroundColor(Color.rgb(15,48,63));
        TextView mark = text("₪", 25, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable badge = rounded(Color.rgb(29,115,119), 14);
        mark.setBackground(badge);
        mark.setPadding(dp(11),dp(7),dp(11),dp(7));
        header.addView(mark);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12),0,0,0);
        titleBox.addView(text("ניהול תקציב",20,Color.WHITE,true));
        titleBox.addView(text("הכסף שלך, בשליטה שלך",11,Color.rgb(197,215,222),false));
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView date = text(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),12,Color.rgb(218,232,236),false);
        header.addView(date);
        root.addView(header);

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1,0,1));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(5),dp(5),dp(5),dp(7));
        bottomBar.setBackgroundColor(Color.WHITE);
        String[] labels = {"ראשי","תנועות","סטטיסטיקה","הגדרות"};
        String[] icons = {"⌂","⇄","▥","⚙"};
        for (int i=0;i<labels.length;i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0,dp(5),0,dp(3));
            TextView icon = text(icons[i],20,Color.rgb(74,96,108),true);
            TextView label = text(labels[i],10,Color.rgb(90,106,116),false);
            icon.setGravity(Gravity.CENTER); label.setGravity(Gravity.CENTER);
            item.addView(icon); item.addView(label);
            item.setOnClickListener(v->{currentTab=tab;render();});
            bottomBar.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(bottomBar);
        setContentView(root);
    }

    private void render() {
        pageHost.removeAllViews();
        if (currentTab == 0) renderHome();
        else if (currentTab == 1) renderTransactions();
        else if (currentTab == 2) renderStatistics();
        else renderSettings();
        updateNavColors();
    }

    private void updateNavColors() {
        for(int i=0;i<bottomBar.getChildCount();i++){
            LinearLayout item=(LinearLayout)bottomBar.getChildAt(i);
            int color=i==currentTab?Color.rgb(25,119,121):Color.rgb(111,126,135);
            ((TextView)item.getChildAt(0)).setTextColor(color);
            ((TextView)item.getChildAt(1)).setTextColor(color);
        }
    }

    private LinearLayout body() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(15),dp(15),dp(15),dp(24));
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(body);
        pageHost.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        return body;
    }

    private void renderHome() {
        LinearLayout b=body();
        LocalDate today=LocalDate.now();
        String monthStart=today.withDayOfMonth(1).toString(), monthEnd=today.withDayOfMonth(today.lengthOfMonth()).toString();
        String yearStart=today.withDayOfYear(1).toString(), yearEnd=today.withDayOfYear(today.lengthOfYear()).toString();
        long income=db.sum("INCOME",monthStart,monthEnd), expense=db.sum("EXPENSE",monthStart,monthEnd);
        long balance=db.balance();
        long mBudget=settingLong("monthly_budget"), yBudget=settingLong("yearly_budget");
        addText(b,"שלום! הנה תמונת המצב שלך",16,Color.rgb(53,72,83),true,0,10);
        LinearLayout balanceCard=card();
        balanceCard.setBackground(rounded(Color.rgb(15,62,76),18));
        balanceCard.addView(text("יתרה כוללת",13,Color.rgb(203,226,229),false));
        balanceCard.addView(text(money(balance),30,Color.WHITE,true));
        balanceCard.addView(text("כולל יתרת פתיחה וכל התנועות שנרשמו",11,Color.rgb(203,226,229),false));
        b.addView(balanceCard,params(-1,-2,0,0,0,14));
        LinearLayout sums=row();
        sums.addView(summaryCard("הכנסות החודש",income,"↗",Color.rgb(27,132,104)),new LinearLayout.LayoutParams(0,-2,1));
        sums.addView(summaryCard("הוצאות החודש",expense,"↘",Color.rgb(191,81,72)),new LinearLayout.LayoutParams(0,-2,1));
        b.addView(sums,params(-1,-2,0,0,0,14));
        addText(b,"תקציבים",16,Color.rgb(53,72,83),true,0,8);
        b.addView(budgetCard("תקציב חודשי",mBudget,expense),params(-1,-2,0,0,0,9));
        long yearlyExpense=db.sum("EXPENSE",yearStart,yearEnd);
        b.addView(budgetCard("תקציב שנתי",yBudget,yearlyExpense),params(-1,-2,0,0,0,15));
        addText(b,"תנועות אחרונות",16,Color.rgb(53,72,83),true,0,8);
        List<BudgetDb.TransactionRecord> recent=db.recentTransactions(5);
        if(recent.isEmpty()) b.addView(emptyMessage("עדיין אין תנועות. הוסף הכנסה או הוצאה כדי להתחיל."));
        for(BudgetDb.TransactionRecord t:recent) b.addView(transactionCard(t),params(-1,-2,0,0,0,8));
        LinearLayout quick=row();
        quick.addView(actionButton("＋ הוצאה",Color.rgb(190,78,70),()->showTransactionDialog("EXPENSE",null)),new LinearLayout.LayoutParams(0,dp(48),1));
        quick.addView(actionButton("＋ הכנסה",Color.rgb(24,128,100),()->showTransactionDialog("INCOME",null)),new LinearLayout.LayoutParams(0,dp(48),1));
        b.addView(quick,params(-1,-2,0,0,0,14));
    }

    private View summaryCard(String label,long amount,String symbol,int color) {
        LinearLayout c=card();
        c.addView(text(symbol,18,color,true));
        c.addView(text(label,11,Color.rgb(104,120,129),false));
        c.addView(text(money(amount),18,Color.rgb(34,54,65),true));
        return c;
    }

    private View budgetCard(String label,long budget,long used) {
        LinearLayout c=card();
        LinearLayout line=row();
        line.addView(text(label,14,Color.rgb(47,68,80),true),new LinearLayout.LayoutParams(0,-2,1));
        line.addView(text(budget<=0?"לא הוגדר":money(budget),13,Color.rgb(69,91,101),true));
        c.addView(line);
        if(budget<=0){c.addView(text("אפשר להגדיר תקציב במסך ההגדרות",11,Color.GRAY,false));return c;}
        int pct=(int)Math.min(100,Math.round(used*100.0/budget));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgress(pct);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(pct>=100?Color.rgb(198,83,74):Color.rgb(30,139,119)));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(231,237,239)));
        c.addView(bar,params(-1,dp(8),0,8,0,3));
        LinearLayout bottom=row();
        bottom.addView(text("נוצלו "+money(used),11,Color.rgb(98,115,124),false),new LinearLayout.LayoutParams(0,-2,1));
        bottom.addView(text(pct+"%",11,pct>=100?Color.rgb(190,70,63):Color.rgb(31,128,105),true));
        c.addView(bottom);
        long remain=budget-used;
        c.addView(text(remain>=0?"נותרו "+money(remain):"חריגה של "+money(-remain),12,remain>=0?Color.rgb(31,128,105):Color.rgb(190,70,63),true));
        return c;
    }

    private void renderTransactions() {
        LinearLayout b=body();
        addText(b,"תנועות",22,Color.rgb(35,57,69),true,0,4);
        addText(b,"כל ההכנסות וההוצאות שלך במקום אחד",12,Color.rgb(111,126,135),false,0,12);
        LinearLayout actions=row();
        actions.addView(actionButton("＋ הוצאה",Color.rgb(190,78,70),()->showTransactionDialog("EXPENSE",null)),new LinearLayout.LayoutParams(0,dp(46),1));
        actions.addView(actionButton("＋ הכנסה",Color.rgb(24,128,100),()->showTransactionDialog("INCOME",null)),new LinearLayout.LayoutParams(0,dp(46),1));
        b.addView(actions,params(-1,-2,0,0,0,12));
        EditText search=edit("חיפוש לפי תיאור, קטגוריה או תאריך",InputType.TYPE_CLASS_TEXT);
        b.addView(search,params(-1,dp(48),0,0,0,8));
        Spinner filter=spinner(new String[]{"הכל","הכנסות בלבד","הוצאות בלבד"});
        b.addView(filter,params(-1,dp(48),0,0,0,12));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);b.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();
            String f=filter.getSelectedItemPosition()==1?"INCOME":filter.getSelectedItemPosition()==2?"EXPENSE":"ALL";
            List<BudgetDb.TransactionRecord> items=db.transactions(search.getText().toString(),f);
            if(items.isEmpty())list.addView(emptyMessage("לא נמצאו תנועות מתאימות."));
            for(BudgetDb.TransactionRecord t:items)list.addView(transactionCard(t),params(-1,-2,0,0,0,8));
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){refresh.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        refresh.run();
    }

    private View transactionCard(BudgetDb.TransactionRecord t) {
        LinearLayout c=card();
        c.setOnClickListener(v->showTransactionActions(t));
        LinearLayout line=row();
        TextView icon=text(t.categoryIcon==null?"📦":t.categoryIcon,23,Color.BLACK,false);icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(Color.rgb(241,246,247),12));icon.setPadding(dp(9),dp(7),dp(9),dp(7));
        line.addView(icon,params(dp(46),dp(46),0,0,0,0));
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(10),0,0,0);
        details.addView(text(t.categoryName,14,Color.rgb(40,61,72),true));
        String description=(t.note==null||t.note.trim().isEmpty())?t.date:t.note+"  ·  "+t.date;
        details.addView(text(description,11,Color.rgb(112,126,134),false));
        line.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        boolean inc="INCOME".equals(t.type);
        line.addView(text((inc?"+":"−")+money(t.amount),15,inc?Color.rgb(27,132,104):Color.rgb(190,78,70),true));
        c.addView(line);
        return c;
    }

    private void showTransactionActions(BudgetDb.TransactionRecord t) {
        new AlertDialog.Builder(this).setTitle(t.categoryIcon+" "+t.categoryName)
            .setItems(new String[]{"עריכת תנועה","מחיקת תנועה"},(d,w)->{
                if(w==0)showTransactionDialog(t.type,t);
                else new AlertDialog.Builder(this).setTitle("מחיקת תנועה")
                    .setMessage("למחוק את התנועה של "+money(t.amount)+"? לא ניתן לבטל את המחיקה.")
                    .setNegativeButton("ביטול",null).setPositiveButton("מחיקה",(x,y)->{db.deleteTransaction(t.id);toast("התנועה נמחקה");render();}).show();
            }).show();
    }

    private void showTransactionDialog(String type,BudgetDb.TransactionRecord existing) {
        List<BudgetDb.Category> cats=db.categories(type);
        if(cats.isEmpty()){toast("אין קטגוריות לסוג זה. הוסף קטגוריה בהגדרות.");return;}
        LinearLayout form=formBox();
        EditText amount=edit("סכום בשקלים",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);amount.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        EditText note=edit("תיאור / הערות (לא חובה)",InputType.TYPE_CLASS_TEXT);
        EditText date=edit("תאריך yyyy-MM-dd",InputType.TYPE_CLASS_TEXT);
        date.setText(LocalDate.now().toString());date.setTextDirection(View.TEXT_DIRECTION_LTR);
        Spinner cat=spinner(cats.toArray(new BudgetDb.Category[0]));
        Spinner payment=spinner(paymentOptions);
        form.addView(fieldLabel("סכום"));form.addView(amount);
        form.addView(fieldLabel("קטגוריה"));form.addView(cat);
        form.addView(fieldLabel("תיאור"));form.addView(note);
        form.addView(fieldLabel("תאריך"));form.addView(date);
        form.addView(fieldLabel("אמצעי תשלום"));form.addView(payment);
        if(existing!=null){
            amount.setText(String.format(Locale.US,"%.2f",existing.amount/100.0));
            note.setText(existing.note==null?"":existing.note);date.setText(existing.date);
            for(int i=0;i<cats.size();i++)if(cats.get(i).id==existing.categoryId){cat.setSelection(i);break;}
            for(int i=0;i<paymentOptions.length;i++)if(paymentOptions[i].equals(existing.payment)){payment.setSelection(i);break;}
        }
        ScrollView sc=new ScrollView(this);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?("הוספת "+("INCOME".equals(type)?"הכנסה":"הוצאה")):"עריכת תנועה")
            .setView(sc).setNegativeButton("ביטול",null).setPositiveButton(existing==null?"שמירה":"עדכון",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            long value=parseMoney(amount.getText().toString());
            String dt=date.getText().toString().trim();
            if(value<=0){amount.setError("הזן סכום תקין");return;}
            if(!validDate(dt)){date.setError("תאריך חייב להיות yyyy-MM-dd");return;}
            BudgetDb.Category selected=(BudgetDb.Category)cat.getSelectedItem();
            if(selected==null){toast("בחר קטגוריה");return;}
            if(existing==null)db.addTransaction(type,value,selected,note.getText().toString().trim(),dt,String.valueOf(payment.getSelectedItem()));
            else db.updateTransaction(existing.id,type,value,selected,note.getText().toString().trim(),dt,String.valueOf(payment.getSelectedItem()));
            dialog.dismiss();toast(existing==null?"התנועה נשמרה":"התנועה עודכנה");render();
        }));
        dialog.show();
    }

    private void renderStatistics() {
        LinearLayout b=body();
        LocalDate today=LocalDate.now();
        String ms=today.withDayOfMonth(1).toString(), me=today.withDayOfMonth(today.lengthOfMonth()).toString();
        String ys=today.withDayOfYear(1).toString(), ye=today.withDayOfYear(today.lengthOfYear()).toString();
        addText(b,"סטטיסטיקה",22,Color.rgb(35,57,69),true,0,3);
        addText(b,"סיכום התנהלות כספית לפי תקופה וקטגוריה",12,Color.rgb(111,126,135),false,0,14);
        addText(b,"החודש הנוכחי",16,Color.rgb(48,68,79),true,0,8);
        LinearLayout month=row();
        month.addView(summaryCard("הכנסות",db.sum("INCOME",ms,me),"↗",Color.rgb(27,132,104)),new LinearLayout.LayoutParams(0,-2,1));
        month.addView(summaryCard("הוצאות",db.sum("EXPENSE",ms,me),"↘",Color.rgb(191,81,72)),new LinearLayout.LayoutParams(0,-2,1));
        b.addView(month,params(-1,-2,0,0,0,8));
        long net=db.sum("INCOME",ms,me)-db.sum("EXPENSE",ms,me);
        b.addView(infoCard("מאזן החודש",money(net),net>=0?Color.rgb(27,132,104):Color.rgb(190,78,70)),params(-1,-2,0,0,0,14));
        addText(b,"מתחילת השנה",16,Color.rgb(48,68,79),true,0,8);
        LinearLayout year=row();
        year.addView(summaryCard("הכנסות",db.sum("INCOME",ys,ye),"↗",Color.rgb(27,132,104)),new LinearLayout.LayoutParams(0,-2,1));
        year.addView(summaryCard("הוצאות",db.sum("EXPENSE",ys,ye),"↘",Color.rgb(191,81,72)),new LinearLayout.LayoutParams(0,-2,1));
        b.addView(year,params(-1,-2,0,0,0,14));
        addText(b,"הוצאות לפי קטגוריה – החודש",16,Color.rgb(48,68,79),true,0,8);
        addBreakdown(b,db.categoryTotals(ms,me,"EXPENSE"),"EXPENSE");
        addText(b,"הכנסות לפי קטגוריה – החודש",16,Color.rgb(48,68,79),true,0,8);
        addBreakdown(b,db.categoryTotals(ms,me,"INCOME"),"INCOME");
        addText(b,"הוצאות לפי קטגוריה – השנה",16,Color.rgb(48,68,79),true,0,8);
        addBreakdown(b,db.categoryTotals(ys,ye,"EXPENSE"),"EXPENSE");
    }

    private void addBreakdown(LinearLayout parent,List<BudgetDb.CategoryTotal> totals,String type) {
        if(totals.isEmpty()){parent.addView(emptyMessage("אין נתונים להצגה בתקופה זו."));return;}
        long max=1;for(BudgetDb.CategoryTotal t:totals)max=Math.max(max,t.total);
        LinearLayout c=card();
        for(BudgetDb.CategoryTotal t:totals){
            LinearLayout line=row();
            line.addView(text(t.icon+" "+t.name,13,Color.rgb(50,69,79),true),new LinearLayout.LayoutParams(0,-2,1));
            line.addView(text(money(t.total),12,Color.rgb(73,94,104),true));
            c.addView(line);
            ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            p.setMax(100);p.setProgress((int)(t.total*100/max));
            p.setProgressTintList(android.content.res.ColorStateList.valueOf("INCOME".equals(type)?Color.rgb(35,140,110):Color.rgb(47,129,165)));
            p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(232,238,240)));
            c.addView(p,params(-1,dp(6),0,dp(5),0,dp(11)));
        }
        parent.addView(c);
    }

    private View infoCard(String label,String value,int color) {
        LinearLayout c=card();c.addView(text(label,12,Color.GRAY,false));c.addView(text(value,23,color,true));return c;
    }

    private void renderSettings() {
        LinearLayout b=body();
        addText(b,"הגדרות",22,Color.rgb(35,57,69),true,0,4);
        addText(b,"התאמה אישית, קטגוריות וגיבוי הנתונים",12,Color.rgb(111,126,135),false,0,13);

        LinearLayout moneySettings=card();
        moneySettings.addView(text("הגדרות תקציב",16,Color.rgb(44,65,76),true));
        EditText opening=edit("יתרת פתיחה בשקלים",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText monthly=edit("תקציב חודשי בשקלים",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText yearly=edit("תקציב שנתי בשקלים",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        opening.setText(formatInput(settingLong("opening_balance")));monthly.setText(formatInput(settingLong("monthly_budget")));yearly.setText(formatInput(settingLong("yearly_budget")));
        moneySettings.addView(fieldLabel("יתרת פתיחה"));moneySettings.addView(opening);
        moneySettings.addView(fieldLabel("תקציב חודשי"));moneySettings.addView(monthly);
        moneySettings.addView(fieldLabel("תקציב שנתי"));moneySettings.addView(yearly);
        moneySettings.addView(actionButton("שמירת הגדרות תקציב",Color.rgb(24,119,121),()->{
            db.setSetting("opening_balance",String.valueOf(parseMoney(opening.getText().toString())));
            db.setSetting("monthly_budget",String.valueOf(parseMoney(monthly.getText().toString())));
            db.setSetting("yearly_budget",String.valueOf(parseMoney(yearly.getText().toString())));
            toast("ההגדרות נשמרו");render();
        }),params(-1,dp(46),0,dp(10),0,0));
        b.addView(moneySettings,params(-1,-2,0,0,0,12));

        LinearLayout categorySection=card();
        LinearLayout ch=row();ch.addView(text("קטגוריות",16,Color.rgb(44,65,76),true),new LinearLayout.LayoutParams(0,-2,1));
        ch.addView(actionButton("＋ הוספה",Color.rgb(24,119,121),()->showCategoryDialog(null)));
        categorySection.addView(ch);
        List<BudgetDb.Category> cats=db.categories("ALL");
        for(BudgetDb.Category c:cats){
            LinearLayout line=row();TextView ico=text(c.icon,21,Color.BLACK,false);line.addView(ico,params(dp(38),dp(38),0,0,0,0));
            LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(8),0,0,0);
            labels.addView(text(c.name,13,Color.rgb(50,69,79),true));labels.addView(text("INCOME".equals(c.type)?"הכנסה":"הוצאה",10,Color.GRAY,false));
            line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            line.setOnClickListener(v->showCategoryActions(c));
            categorySection.addView(line,params(-1,-2,0,dp(8),0,0));
            addDivider(categorySection);
        }
        b.addView(categorySection,params(-1,-2,0,0,0,12));

        LinearLayout recurringSection=card();
        LinearLayout rh=row();rh.addView(text("הוראות מחזוריות",16,Color.rgb(44,65,76),true),new LinearLayout.LayoutParams(0,-2,1));
        rh.addView(actionButton("＋ הוספה",Color.rgb(24,119,121),()->chooseRecurringType()));
        recurringSection.addView(rh);
        List<BudgetDb.RecurringRule> rules=db.recurringRules();
        if(rules.isEmpty())recurringSection.addView(text("אין הוראות קבועות. אפשר להוסיף משכורת או תשלום שחוזר מדי חודש.",11,Color.GRAY,false));
        for(BudgetDb.RecurringRule r:rules){
            LinearLayout line=row();
            LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(r.categoryIcon+" "+r.categoryName+" · "+money(r.amount),13,Color.rgb(50,69,79),true));
            labels.addView(text(frequencyHeb(r.frequency)+" · הבא: "+r.nextDue+(r.active?"":" · מושהה"),10,Color.GRAY,false));
            line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            line.addView(actionButton(r.active?"השהיה":"הפעלה",r.active?Color.rgb(106,123,132):Color.rgb(24,119,121),()->{db.toggleRecurring(r.id,!r.active);render();}));
            line.setOnLongClickListener(v->{new AlertDialog.Builder(this).setMessage("למחוק את ההוראה המחזורית? תנועות שכבר נוצרו יישארו.").setNegativeButton("ביטול",null).setPositiveButton("מחיקה",(d,w)->{db.deleteRecurring(r.id);render();}).show();return true;});
            recurringSection.addView(line,params(-1,-2,0,dp(9),0,0));addDivider(recurringSection);
        }
        b.addView(recurringSection,params(-1,-2,0,0,0,12));

        LinearLayout backup=card();
        backup.addView(text("גיבוי וייצוא",16,Color.rgb(44,65,76),true));
        backup.addView(text("המידע נשמר במכשיר. מומלץ ליצור גיבוי לפני החלפת מכשיר או התקנה מחדש.",11,Color.GRAY,false));
        backup.addView(actionButton("יצירת גיבוי מלא (JSON)",Color.rgb(24,119,121),()->backupIntent()),params(-1,dp(45),0,dp(10),0,0));
        backup.addView(actionButton("ייצוא תנועות ל־CSV",Color.rgb(55,105,132),()->csvIntent()),params(-1,dp(45),0,dp(8),0,0));
        backup.addView(actionButton("שחזור מגיבוי",Color.rgb(105,112,126),()->restoreIntent()),params(-1,dp(45),0,dp(8),0,0));
        b.addView(backup,params(-1,-2,0,0,0,12));
        b.addView(infoCard("גרסה","ניהול תקציב 1.0.0",Color.rgb(49,91,110)),params(-1,-2,0,0,0,10));
        addText(b,"הנתונים נשמרים מקומית בלבד. האפליקציה אינה דורשת הרשאת אינטרנט.",11,Color.rgb(115,128,136),false,0,10);
    }

    private void showCategoryActions(BudgetDb.Category c) {
        new AlertDialog.Builder(this).setTitle(c.icon+" "+c.name).setItems(new String[]{"עריכת קטגוריה","מחיקת קטגוריה"},(d,w)->{
            if(w==0)showCategoryDialog(c);
            else new AlertDialog.Builder(this).setTitle("מחיקת קטגוריה").setMessage("למחוק את הקטגוריה? תנועות קודמות ישמרו את שם הקטגוריה והאייקון שלהן.").setNegativeButton("ביטול",null).setPositiveButton("מחיקה",(x,y)->{db.deleteCategory(c.id);render();}).show();
        }).show();
    }

    private void showCategoryDialog(BudgetDb.Category existing) {
        LinearLayout form=formBox();
        EditText name=edit("שם הקטגוריה",InputType.TYPE_CLASS_TEXT);
        Spinner type=spinner(new String[]{"EXPENSE","INCOME"});
        type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,new String[]{"הוצאה","הכנסה"}){{setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);}});
        Spinner icon=spinner(emojiOptions);
        EditText mb=edit("תקציב חודשי לקטגוריה (אופציונלי)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText yb=edit("תקציב שנתי לקטגוריה (אופציונלי)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(existing!=null){name.setText(existing.name);type.setSelection("INCOME".equals(existing.type)?1:0);for(int i=0;i<emojiOptions.length;i++)if(emojiOptions[i].equals(existing.icon)){icon.setSelection(i);break;}mb.setText(formatInput(existing.monthlyBudget));yb.setText(formatInput(existing.yearlyBudget));}
        form.addView(fieldLabel("שם"));form.addView(name);form.addView(fieldLabel("סוג"));form.addView(type);
        form.addView(fieldLabel("אייקון"));form.addView(icon);form.addView(fieldLabel("תקציב חודשי"));form.addView(mb);form.addView(fieldLabel("תקציב שנתי"));form.addView(yb);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"הוספת קטגוריה":"עריכת קטגוריה").setView(form).setNegativeButton("ביטול",null).setPositiveButton("שמירה",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("נדרש שם");return;}
            String t=type.getSelectedItemPosition()==1?"INCOME":"EXPENSE";String ic=String.valueOf(icon.getSelectedItem());
            long m=parseMoney(mb.getText().toString()),y=parseMoney(yb.getText().toString());
            if(existing==null)db.addCategory(n,ic,t,m,y);else db.updateCategory(existing.id,n,ic,t,m,y);
            dialog.dismiss();toast("הקטגוריה נשמרה");render();
        }));
        dialog.show();
    }

    private void chooseRecurringType() {
        new AlertDialog.Builder(this).setTitle("סוג ההוראה").setItems(new String[]{"הוצאה קבועה","הכנסה קבועה"},(d,w)->showRecurringDialog(w==1?"INCOME":"EXPENSE")).show();
    }

    private void showRecurringDialog(String type) {
        List<BudgetDb.Category> cats=db.categories(type);if(cats.isEmpty()){toast("אין קטגוריות מתאימות");return;}
        LinearLayout form=formBox();
        EditText amount=edit("סכום בשקלים",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner cat=spinner(cats.toArray(new BudgetDb.Category[0]));
        EditText note=edit("תיאור",InputType.TYPE_CLASS_TEXT);
        Spinner freq=spinner(frequencyLabels);
        EditText next=edit("תאריך חיוב ראשון yyyy-MM-dd",InputType.TYPE_CLASS_TEXT);next.setText(LocalDate.now().toString());next.setTextDirection(View.TEXT_DIRECTION_LTR);
        EditText end=edit("תאריך סיום yyyy-MM-dd (לא חובה)",InputType.TYPE_CLASS_TEXT);end.setTextDirection(View.TEXT_DIRECTION_LTR);
        form.addView(fieldLabel("סכום"));form.addView(amount);form.addView(fieldLabel("קטגוריה"));form.addView(cat);
        form.addView(fieldLabel("תיאור"));form.addView(note);form.addView(fieldLabel("תדירות"));form.addView(freq);
        form.addView(fieldLabel("תאריך ראשון"));form.addView(next);form.addView(fieldLabel("תאריך סיום"));form.addView(end);
        ScrollView sc=new ScrollView(this);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("הוספת הוראה מחזורית").setView(sc).setNegativeButton("ביטול",null).setPositiveButton("שמירה",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            long value=parseMoney(amount.getText().toString());String n=next.getText().toString().trim(),e=end.getText().toString().trim();
            if(value<=0){amount.setError("הזן סכום תקין");return;}if(!validDate(n)){next.setError("תאריך לא תקין");return;}if(!e.isEmpty()&&!validDate(e)){end.setError("תאריך לא תקין");return;}if(!e.isEmpty()&&e.compareTo(n)<0){end.setError("תאריך הסיום קודם לתאריך ההתחלה");return;}
            db.addRecurring(type,value,(BudgetDb.Category)cat.getSelectedItem(),note.getText().toString().trim(),frequencyCodes[freq.getSelectedItemPosition()],n,e);
            db.processRecurring();dialog.dismiss();toast("ההוראה נשמרה");render();
        }));
        dialog.show();
    }

    private String frequencyHeb(String code) {
        for(int i=0;i<frequencyCodes.length;i++)if(frequencyCodes[i].equals(code))return frequencyLabels[i];
        return "חודשי";
    }

    private void backupIntent() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"nihul-taktziv-backup-"+LocalDate.now()+".json");startActivityForResult(i,REQ_BACKUP);
    }
    private void csvIntent() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"nihul-taktziv-transactions-"+LocalDate.now()+".csv");startActivityForResult(i,REQ_CSV);
    }
    private void restoreIntent() {
        new AlertDialog.Builder(this).setTitle("שחזור גיבוי").setMessage("השחזור יחליף את כל הנתונים הקיימים באפליקציה בנתונים מהקובץ. מומלץ ליצור גיבוי לפני ההמשך.").setNegativeButton("ביטול",null).setPositiveButton("בחירת קובץ",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,REQ_RESTORE);}).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try {
            if(requestCode==REQ_BACKUP){try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(db.exportJson().getBytes(StandardCharsets.UTF_8));}toast("הגיבוי נוצר בהצלחה");}
            else if(requestCode==REQ_CSV){try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(db.exportCsv().getBytes(StandardCharsets.UTF_8));}toast("קובץ CSV נשמר");}
            else if(requestCode==REQ_RESTORE){StringBuilder s=new StringBuilder();try(InputStream in=getContentResolver().openInputStream(uri)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)s.append(new String(buf,0,n,StandardCharsets.UTF_8));}db.importJson(s.toString());db.processRecurring();toast("הגיבוי שוחזר בהצלחה");render();}
        } catch(Exception e){new AlertDialog.Builder(this).setTitle("לא ניתן להשלים את הפעולה").setMessage(e.getMessage()==null?"שגיאה בקריאת הקובץ":e.getMessage()).setPositiveButton("אישור",null).show();}
    }

    private LinearLayout card() {
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));
        c.setBackground(rounded(Color.WHITE,15));return c;
    }
    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),Color.rgb(231,237,240));return d;
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    private LinearLayout formBox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(10),dp(16),dp(8));l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return t;}
    private void addText(LinearLayout p,String s,int size,int color,boolean bold,int top,int bottom){TextView t=text(s,size,color,bold);p.addView(t,params(-1,-2,0,top,0,bottom));}
    private TextView fieldLabel(String s){TextView t=text(s,12,Color.rgb(76,96,106),true);t.setPadding(0,dp(9),0,dp(4));return t;}
    private EditText edit(String hint,int inputType){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setHint(hint);e.setInputType(inputType);e.setPadding(dp(12),0,dp(12),0);e.setBackground(rounded(Color.rgb(248,250,251),10));e.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return e;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
    private Spinner spinner(Object[] values){Spinner s=new Spinner(this);ArrayAdapter<Object>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
    private View actionButton(String label,int color,Runnable action){
        TextView v=text(label,13,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(5),dp(10),dp(5));v.setBackground(rounded(color,11));v.setOnClickListener(w->action.run());return v;
    }
    private View emptyMessage(String msg){LinearLayout c=card();TextView t=text(msg,12,Color.GRAY,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),dp(10),dp(8),dp(10));c.addView(t);return c;}
    private void addDivider(LinearLayout p){View v=new View(this);v.setBackgroundColor(Color.rgb(237,241,243));p.addView(v,params(-1,dp(1),0,dp(8),0,0));}
    private LinearLayout.LayoutParams params(int w,int h,int left,int top,int right,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));p.setMargins(dp(left),dp(top),dp(right),dp(bottom));return p;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private long settingLong(String key){try{return Long.parseLong(db.getSetting(key,"0"));}catch(Exception e){return 0;}}
    private long parseMoney(String value){
        try{String s=value.replace("₪","").replace(",","").replace(" ","").trim();if(s.isEmpty())return 0;java.math.BigDecimal d=new java.math.BigDecimal(s).setScale(2,java.math.RoundingMode.HALF_UP);return d.movePointRight(2).longValueExact();}catch(Exception e){return -1;}
    }
    private String money(long cents){return "₪"+String.format(Locale.US,"%,.2f",cents/100.0);}
    private String formatInput(long cents){return String.format(Locale.US,"%.2f",cents/100.0);}
    private boolean validDate(String d){try{LocalDate.parse(d);return true;}catch(DateTimeParseException e){return false;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
