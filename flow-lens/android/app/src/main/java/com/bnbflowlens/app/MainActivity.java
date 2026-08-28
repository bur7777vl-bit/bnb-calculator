package com.bnbflowlens.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final DecimalFormat p2 = new DecimalFormat("0.00");
    private final DecimalFormat q1 = new DecimalFormat("0.0");

    private LinearLayout asksBox, bidsBox, dots, tfRow;
    private ViewFlipper pager;
    private TextView priceText, changeText, oiText, oiDeltaText, takerText, ratioText, domBiasText, statusText;
    private TextView ctxPrice, ctxRange, ctxVolume, ctxTaker, ctxOi, ctxRatio, ctxSummary;
    private String tf = "15m";
    private double step = 1.0;
    private int levels = 9;
    private boolean running = true;
    private float touchX, touchY;
    private boolean swipeHandled = false;
    private final Map<String,Integer> holdCounts = new HashMap<>();
    private final Map<String,Button> tfButtons = new HashMap<>();

    private final int BG = Color.rgb(10,13,18);
    private final int CARD = Color.rgb(18,23,31);
    private final int CARD2 = Color.rgb(22,28,38);
    private final int LINE = Color.rgb(41,49,62);
    private final int TEXT = Color.rgb(232,236,242);
    private final int MUTED = Color.rgb(139,151,169);
    private final int GREEN = Color.rgb(58,201,128);
    private final int RED = Color.rgb(242,92,110);
    private final int BLUE = Color.rgb(86,154,255);
    private final int AMBER = Color.rgb(247,184,67);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        refresh();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (pager != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchX = e.getRawX();
                touchY = e.getRawY();
                swipeHandled = false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP && !swipeHandled) {
                float dx = e.getRawX() - touchX;
                float dy = e.getRawY() - touchY;
                if (Math.abs(dx) >= dp(55) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    int cur = pager.getDisplayedChild();
                    if (dx > 0 && cur > 0) {
                        goToPage(cur - 1, true);
                        swipeHandled = true;
                    } else if (dx < 0 && cur < pager.getChildCount() - 1) {
                        goToPage(cur + 1, false);
                        swipeHandled = true;
                    }
                }
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override protected void onDestroy() {
        running = false;
        io.shutdownNow();
        super.onDestroy();
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(8),dp(7),dp(8),dp(7));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.setPadding(dp(12),dp(7),dp(12),dp(8));

        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("BNB FLOW LENS",19,TEXT,true);
        TextView tag = tv("PERP",11,AMBER,true);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(46),1)); head.addView(tag);
        shell.addView(head);

        LinearLayout priceCard = card();
        priceText = tv("—",31,TEXT,true); changeText = tv("BNBUSDT",13,MUTED,true);
        priceCard.addView(priceText); priceCard.addView(changeText); shell.addView(priceCard);

        tfRow = new LinearLayout(this); tfRow.setGravity(Gravity.CENTER); tfRow.setPadding(0,dp(2),0,dp(2));
        addTfButton("M15","15m"); addTfButton("H1","1h"); addTfButton("H4","4h");
        shell.addView(tfRow);

        dots = new LinearLayout(this); dots.setGravity(Gravity.CENTER); dots.setPadding(0,dp(2),0,dp(3)); shell.addView(dots);

        pager = new ViewFlipper(this);
        pager.setMeasureAllChildren(false);
        pager.addView(domPage());
        pager.addView(overviewPage());
        pager.addView(contextPage());
        pager.setDisplayedChild(1);
        shell.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
        updateDots(); updateTfButtons();

        setContentView(shell);
    }

    private void addTfButton(String label,String value){
        Button b=smallButton(label); tfButtons.put(value,b);
        b.setOnClickListener(v->{ tf=value; updateTfButtons(); refresh(); });
        tfRow.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));
    }

    private View overviewPage(){
        ScrollView sv=pageScroll(); LinearLayout root=pageRoot(); sv.addView(root);
        root.addView(section("ОБЗОР РЫНКА"));
        LinearLayout row1=new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout a=metricCard(), b=metricCard(); oiText=tv("OI —",17,TEXT,true); oiDeltaText=tv("Δ —",12,MUTED,false); a.addView(oiText); a.addView(oiDeltaText);
        takerText=tv("Taker —",17,TEXT,true); ratioText=tv("L/S —",12,MUTED,false); b.addView(takerText); b.addView(ratioText);
        row1.addView(a,new LinearLayout.LayoutParams(0,-2,1)); row1.addView(b,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row1);

        LinearLayout hint=card();
        hint.addView(tv("Свайп вправо  →  DOM",13,MUTED,true));
        hint.addView(tv("Свайп влево   →  Контекст",13,MUTED,true));
        root.addView(hint);
        statusText=tv("Подключение к Binance Futures…",12,MUTED,false); root.addView(statusText);
        root.addView(tv("Публичные данные Binance Futures · без API-ключей · обновление ~1.5 сек",11,MUTED,false));
        return sv;
    }

    private View domPage(){
        ScrollView sv=pageScroll(); LinearLayout root=pageRoot(); sv.addView(root);
        root.addView(section("DOM · СТАКАН"));
        LinearLayout selectors=new LinearLayout(this); selectors.setGravity(Gravity.CENTER_VERTICAL);
        Spinner stepSpin=new Spinner(this); String[] steps={"0.1","0.5","1","2","5","10"}; stepSpin.setAdapter(spinnerAdapter(steps)); stepSpin.setSelection(2);
        stepSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ step=Double.parseDouble(steps[pos]); refresh(); }});
        Spinner levelSpin=new Spinner(this); String[] lev={"3","6","9","12"}; levelSpin.setAdapter(spinnerAdapter(lev)); levelSpin.setSelection(2);
        levelSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ levels=Integer.parseInt(lev[pos]); refresh(); }});
        selectors.addView(tv("Шаг",12,MUTED,true)); selectors.addView(stepSpin,new LinearLayout.LayoutParams(0,dp(46),1)); selectors.addView(tv("Уровни",12,MUTED,true)); selectors.addView(levelSpin,new LinearLayout.LayoutParams(0,dp(46),1)); root.addView(selectors);

        LinearLayout domCard=card(); asksBox=new LinearLayout(this); asksBox.setOrientation(LinearLayout.VERTICAL); bidsBox=new LinearLayout(this); bidsBox.setOrientation(LinearLayout.VERTICAL);
        domBiasText=tv("DOM —",13,MUTED,true);
        domCard.addView(tv("ASKS",12,RED,true)); domCard.addView(asksBox); domCard.addView(domBiasText); domCard.addView(bidsBox); domCard.addView(tv("BIDS",12,GREEN,true)); root.addView(domCard);
        return sv;
    }

    private View contextPage(){
        ScrollView sv=pageScroll(); LinearLayout root=pageRoot(); sv.addView(root);
        root.addView(section("КОНТЕКСТ ТАЙМФРЕЙМА"));
        LinearLayout candle=card();
        ctxPrice=tv("Свеча —",17,TEXT,true); ctxRange=tv("High / Low —",13,MUTED,false); ctxVolume=tv("Volume —",13,MUTED,false);
        candle.addView(ctxPrice); candle.addView(ctxRange); candle.addView(ctxVolume); root.addView(candle);
        LinearLayout flow=card();
        ctxTaker=tv("Taker —",15,TEXT,true); ctxOi=tv("OI —",15,TEXT,true); ctxRatio=tv("Long/Short —",15,TEXT,true);
        flow.addView(ctxTaker); flow.addView(ctxOi); flow.addView(ctxRatio); root.addView(flow);
        LinearLayout summary=card(); summary.setBackgroundColor(CARD2); ctxSummary=tv("Сводка —",14,TEXT,true); summary.addView(ctxSummary); root.addView(summary);
        root.addView(tv("Контекст меняется вместе с M15 / H1 / H4 на верхней панели.",11,MUTED,false));
        return sv;
    }

    private void goToPage(int target, boolean right) {
        if (pager == null || target < 0 || target >= pager.getChildCount() || target == pager.getDisplayedChild()) return;
        animatePager(right);
        pager.setDisplayedChild(target);
        updateDots();
    }

    private void animatePager(boolean right){
        int w=getResources().getDisplayMetrics().widthPixels;
        Animation in=new TranslateAnimation(right?-w:w,0,0,0); in.setDuration(180);
        Animation out=new TranslateAnimation(0,right?w:-w,0,0); out.setDuration(180);
        pager.setInAnimation(in); pager.setOutAnimation(out);
    }

    private void updateDots(){
        dots.removeAllViews(); int cur=pager==null?1:pager.getDisplayedChild();
        for(int i=0;i<3;i++){
            final int target=i;
            TextView d=tv(i==cur?"●":"○",16,i==cur?BLUE:MUTED,true);
            d.setGravity(Gravity.CENTER);
            d.setPadding(dp(10),dp(4),dp(10),dp(4));
            d.setClickable(true);
            d.setOnClickListener(v->{ int from=pager.getDisplayedChild(); goToPage(target,target<from); });
            dots.addView(d);
        }
    }

    private void updateTfButtons(){
        for(Map.Entry<String,Button> e:tfButtons.entrySet()){ boolean on=e.getKey().equals(tf); e.getValue().setTextColor(on?BLUE:TEXT); e.getValue().setTypeface(Typeface.DEFAULT,on?Typeface.BOLD:Typeface.NORMAL); }
    }

    private ScrollView pageScroll(){ ScrollView s=new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(BG); return s; }
    private LinearLayout pageRoot(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,0,0,dp(12)); return l; }
    private ArrayAdapter<String> spinnerAdapter(String[] x){ ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,x); return a; }
    private LinearLayout card(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(8),dp(8),dp(8),dp(8)); l.setBackgroundColor(CARD); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(4),0,dp(4)); l.setLayoutParams(p); return l; }
    private LinearLayout metricCard(){ LinearLayout l=card(); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(2),dp(4),dp(2),dp(4)); l.setLayoutParams(p); return l; }
    private TextView section(String s){ TextView v=tv(s,12,MUTED,true); v.setPadding(dp(4),dp(8),0,dp(3)); return v; }
    private Button smallButton(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(13); b.setAllCaps(false); b.setBackgroundColor(CARD); return b; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void refresh(){ if(!running)return; io.execute(()->{ try { Snapshot s=load(); ui.post(()->render(s)); } catch(Exception e){ ui.post(()->{ if(statusText!=null) statusText.setText("Ошибка сети: "+e.getClass().getSimpleName()); }); } finally { if(running) ui.postDelayed(this::refresh,1500); } }); }

    private Snapshot load() throws Exception {
        Snapshot s=new Snapshot();
        JSONObject ticker=new JSONObject(get("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=BNBUSDT"));
        s.price=ticker.getDouble("lastPrice"); s.change=ticker.getDouble("priceChangePercent");
        JSONObject oi=new JSONObject(get("https://fapi.binance.com/fapi/v1/openInterest?symbol=BNBUSDT")); s.oi=oi.getDouble("openInterest");
        JSONArray oih=new JSONArray(get("https://fapi.binance.com/futures/data/openInterestHist?symbol=BNBUSDT&period="+tf+"&limit=2"));
        if(oih.length()>=2){ double a=oih.getJSONObject(0).getDouble("sumOpenInterest"); double b=oih.getJSONObject(1).getDouble("sumOpenInterest"); s.oiDelta=a==0?0:(b-a)/a*100.0; }
        JSONArray tak=new JSONArray(get("https://fapi.binance.com/futures/data/takerlongshortRatio?symbol=BNBUSDT&period="+tf+"&limit=1"));
        if(tak.length()>0){ JSONObject t=tak.getJSONObject(0); s.buyVol=t.getDouble("buyVol"); s.sellVol=t.getDouble("sellVol"); }
        JSONArray ls=new JSONArray(get("https://fapi.binance.com/futures/data/globalLongShortAccountRatio?symbol=BNBUSDT&period="+tf+"&limit=1")); if(ls.length()>0) s.ls=ls.getJSONObject(0).getDouble("longShortRatio");
        JSONArray kl=new JSONArray(get("https://fapi.binance.com/fapi/v1/klines?symbol=BNBUSDT&interval="+tf+"&limit=2"));
        if(kl.length()>0){ JSONArray k=kl.getJSONArray(kl.length()-1); s.open=Double.parseDouble(k.getString(1)); s.high=Double.parseDouble(k.getString(2)); s.low=Double.parseDouble(k.getString(3)); s.close=Double.parseDouble(k.getString(4)); s.volume=Double.parseDouble(k.getString(5)); }
        JSONObject depth=new JSONObject(get("https://fapi.binance.com/fapi/v1/depth?symbol=BNBUSDT&limit=1000"));
        s.asks=aggregate(depth.getJSONArray("asks"),true,s.price); s.bids=aggregate(depth.getJSONArray("bids"),false,s.price);
        return s;
    }

    private List<Level> aggregate(JSONArray arr, boolean ask, double price) throws Exception {
        TreeMap<Double,Double> map=new TreeMap<>();
        for(int i=0;i<arr.length();i++){ JSONArray r=arr.getJSONArray(i); double p=Double.parseDouble(r.getString(0)), q=Double.parseDouble(r.getString(1)); double bucket=ask?Math.ceil(p/step)*step:Math.floor(p/step)*step; map.put(bucket,map.getOrDefault(bucket,0.0)+q); }
        List<Level> out=new ArrayList<>(); NavigableMap<Double,Double> nav=ask?map.tailMap(price,true):map.headMap(price,true).descendingMap();
        int n=0; for(Map.Entry<Double,Double> e:nav.entrySet()){ if(n++>=levels)break; out.add(new Level(e.getKey(),e.getValue())); }
        return out;
    }

    private void render(Snapshot s){
        priceText.setText(p2.format(s.price)); priceText.setTextColor(s.change>=0?GREEN:RED);
        changeText.setText("BNBUSDT PERP   "+(s.change>=0?"+":"")+p2.format(s.change)+"%   ·   "+tfLabel());
        double total=s.buyVol+s.sellVol, buyPct=total==0?0:s.buyVol/total*100;
        oiText.setText("OI  "+compact(s.oi)+" BNB"); oiDeltaText.setText("Δ "+(s.oiDelta>=0?"+":"")+p2.format(s.oiDelta)+"%"); oiDeltaText.setTextColor(s.oiDelta>=0?GREEN:RED);
        takerText.setText("Taker  "+p2.format(buyPct)+"% BUY"); takerText.setTextColor(buyPct>=50?GREEN:RED); ratioText.setText("Long/Short  "+p2.format(s.ls));
        double bq=sum(s.bids), aq=sum(s.asks), dom=aq+bq==0?50:bq/(aq+bq)*100;
        domBiasText.setText("DOM  bids "+p2.format(dom)+"%   ·   asks "+p2.format(100-dom)+"%"); domBiasText.setTextColor(dom>=50?GREEN:RED);
        renderLevels(asksBox,s.asks,true); renderLevels(bidsBox,s.bids,false);
        if(statusText!=null) statusText.setText("LIVE · "+tfLabel()+" · обновление ~1.5 сек");

        double candlePct=s.open==0?0:(s.close-s.open)/s.open*100;
        ctxPrice.setText(tfLabel()+" свеча  "+p2.format(s.open)+" → "+p2.format(s.close)+"  ("+(candlePct>=0?"+":"")+p2.format(candlePct)+"%)"); ctxPrice.setTextColor(candlePct>=0?GREEN:RED);
        ctxRange.setText("High "+p2.format(s.high)+"   ·   Low "+p2.format(s.low)+"   ·   Range "+p2.format(s.high-s.low));
        ctxVolume.setText("Volume  "+compact(s.volume)+" BNB");
        ctxTaker.setText("Taker:  BUY "+p2.format(buyPct)+"%   /   SELL "+p2.format(100-buyPct)+"%"); ctxTaker.setTextColor(buyPct>=50?GREEN:RED);
        ctxOi.setText("OI: "+compact(s.oi)+" BNB   ·   Δ "+(s.oiDelta>=0?"+":"")+p2.format(s.oiDelta)+"%");
        ctxRatio.setText("Long/Short: "+p2.format(s.ls));
        ctxSummary.setText(summaryText(candlePct,buyPct,s.oiDelta,dom));
    }

    private String summaryText(double candlePct,double buyPct,double oiDelta,double dom){
        int bull=0,bear=0; if(candlePct>0)bull++; else if(candlePct<0)bear++; if(buyPct>52)bull++; else if(buyPct<48)bear++; if(dom>52)bull++; else if(dom<48)bear++;
        String tone=bull>bear?"Покупатели сильнее":bear>bull?"Продавцы сильнее":"Баланс";
        String oi=oiDelta>0?"OI растёт — позиции добавляются":oiDelta<0?"OI снижается — позиции сокращаются":"OI почти без изменения";
        return tone+"\n"+oi+"\nНе сигнал на вход: смотри реакцию цены у уровней.";
    }

    private String tfLabel(){ if("15m".equals(tf))return "M15"; if("1h".equals(tf))return "H1"; return "H4"; }

    private void renderLevels(LinearLayout box,List<Level> ls,boolean ask){
        box.removeAllViews(); List<Level> rank=new ArrayList<>(ls); rank.sort((a,b)->Double.compare(b.qty,a.qty)); Set<Double> top=new HashSet<>(); for(int i=0;i<Math.min(3,rank.size());i++) top.add(rank.get(i).price);
        List<Level> draw=ls; if(ask){ draw=new ArrayList<>(ls); Collections.reverse(draw); }
        Set<String> now=new HashSet<>(); for(Level l:draw){ String k=(ask?"A":"B")+":"+p2.format(l.price); now.add(k); int h=holdCounts.getOrDefault(k,0)+1; holdCounts.put(k,h); LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); if(top.contains(l.price)) row.setBackgroundColor(ask?Color.rgb(61,25,32):Color.rgb(20,56,42)); TextView p=tv(p2.format(l.price),14,ask?RED:GREEN,true); TextView q=tv(q1.format(l.qty)+" BNB"+(h>=3?"  ×"+h:""),13,TEXT,top.contains(l.price)); row.addView(p,new LinearLayout.LayoutParams(0,dp(38),1)); row.addView(q,new LinearLayout.LayoutParams(0,dp(38),1)); box.addView(row); View line=new View(this); line.setBackgroundColor(LINE); box.addView(line,new LinearLayout.LayoutParams(-1,1)); }
        holdCounts.keySet().removeIf(k->k.startsWith(ask?"A:":"B:")&&!now.contains(k));
    }

    private double sum(List<Level> ls){ double x=0; for(Level l:ls)x+=l.qty; return x; }
    private String compact(double v){ if(v>=1_000_000)return p2.format(v/1_000_000)+"M"; if(v>=1000)return p2.format(v/1000)+"K"; return p2.format(v); }
    private String get(String u) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(7000); c.setReadTimeout(7000); c.setRequestProperty("User-Agent","BNB-Flow-Lens-Android/1.2"); int code=c.getResponseCode(); if(code!=200)throw new RuntimeException("HTTP "+code); BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); r.close(); c.disconnect(); return b.toString(); }

    static class Level { double price,qty; Level(double p,double q){price=p;qty=q;} }
    static class Snapshot { double price,change,oi,oiDelta,buyVol,sellVol,ls,open,high,low,close,volume; List<Level> asks,bids; }
}
