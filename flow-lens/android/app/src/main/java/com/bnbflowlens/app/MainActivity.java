package com.bnbflowlens.app;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final DecimalFormat p2 = new DecimalFormat("0.00");
    private final DecimalFormat q1 = new DecimalFormat("0.0");
    private final Map<String,Integer> holdCounts = new HashMap<>();
    private final Map<String,Button> tfButtons = new HashMap<>();

    private ViewFlipper pager;
    private LinearLayout dots, tfRow, asksBox, bidsBox;
    private TextView priceText, changeText, oiText, oiDeltaText, takerText, ratioText, domBiasText, statusText;
    private TextView ctxPrice, ctxRange, ctxVolume, ctxTaker, ctxOi, ctxRatio, ctxSummary;
    private SparkView priceChart, oiChart, ctxPriceChart;
    private String tf="15m";
    private double step=1.0;
    private int levels=9;
    private boolean running=true;
    private float downX, downY;

    private final int BG=Color.rgb(10,13,18), CARD=Color.rgb(18,23,31), CARD2=Color.rgb(22,28,38);
    private final int LINE=Color.rgb(41,49,62), TEXT=Color.rgb(232,236,242), MUTED=Color.rgb(139,151,169);
    private final int GREEN=Color.rgb(58,201,128), RED=Color.rgb(242,92,110), BLUE=Color.rgb(86,154,255), AMBER=Color.rgb(247,184,67);

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); refresh(); }
    @Override protected void onDestroy(){ running=false; io.shutdownNow(); super.onDestroy(); }

    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){ downX=e.getX(); downY=e.getY(); }
        else if(e.getAction()==MotionEvent.ACTION_UP){
            float dx=e.getX()-downX, dy=e.getY()-downY;
            if(Math.abs(dx)>dp(70) && Math.abs(dx)>Math.abs(dy)*1.25f){ changePage(dx>0?-1:1); return true; }
        }
        return super.dispatchTouchEvent(e);
    }

    private void buildUi(){
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setBackgroundColor(BG); shell.setPadding(dp(12),dp(7),dp(12),dp(8));
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tv("BNB FLOW LENS",19,TEXT,true),new LinearLayout.LayoutParams(0,dp(48),1)); head.addView(tv("PERP",12,AMBER,true)); shell.addView(head);
        LinearLayout pc=card(); priceText=tv("—",32,TEXT,true); changeText=tv("BNBUSDT PERP",14,MUTED,true); pc.addView(priceText); pc.addView(changeText); shell.addView(pc);
        tfRow=new LinearLayout(this); tfRow.setGravity(Gravity.CENTER); addTfButton("M15","15m"); addTfButton("H1","1h"); addTfButton("H4","4h"); shell.addView(tfRow);
        dots=new LinearLayout(this); dots.setGravity(Gravity.CENTER); dots.setPadding(0,dp(4),0,dp(5)); shell.addView(dots);
        pager=new ViewFlipper(this); pager.addView(domPage()); pager.addView(overviewPage()); pager.addView(contextPage()); pager.setDisplayedChild(1); shell.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell); updateDots(); updateTfButtons();
    }

    private void addTfButton(String label,String value){ Button b=smallButton(label); tfButtons.put(value,b); b.setOnClickListener(v->{ tf=value; updateTfButtons(); refresh(); }); tfRow.addView(b,new LinearLayout.LayoutParams(0,dp(48),1)); }

    private View overviewPage(){
        ScrollView sv=pageScroll(); LinearLayout root=pageRoot(); sv.addView(root); root.addView(section("ОБЗОР РЫНКА"));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); LinearLayout a=metricCard(), b=metricCard();
        oiText=tv("OI —",18,TEXT,true); oiDeltaText=tv("Δ —",14,MUTED,true); a.addView(oiText); a.addView(oiDeltaText);
        takerText=tv("Taker —",18,TEXT,true); ratioText=tv("Long/Short —",14,MUTED,true); b.addView(takerText); b.addView(ratioText);
        row.addView(a,new LinearLayout.LayoutParams(0,-2,1)); row.addView(b,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row);
        LinearLayout chartCard=card(); chartCard.addView(tv("Цена · последние свечи",15,TEXT,true)); priceChart=new SparkView(this,BLUE); chartCard.addView(priceChart,new LinearLayout.LayoutParams(-1,dp(145))); root.addView(chartCard);
        LinearLayout oiCard=card(); oiCard.addView(tv("Open Interest · динамика",15,TEXT,true)); oiChart=new SparkView(this,AMBER); oiCard.addView(oiChart,new LinearLayout.LayoutParams(-1,dp(105))); root.addView(oiCard);
        statusText=tv("Подключение к Binance Futures…",14,MUTED,false); root.addView(statusText); return sv;
    }

    private View domPage(){
        ScrollView sv=pageScroll(); LinearLayout root=pageRoot(); sv.addView(root); root.addView(section("DOM · СТАКАН"));
        LinearLayout selectors=new LinearLayout(this); selectors.setGravity(Gravity.CENTER_VERTICAL);
        String[] steps={"0.1","0.5","1","2","5","10"}; Spinner stepSpin=new Spinner(this); stepSpin.setAdapter(spinnerAdapter(steps)); stepSpin.setSelection(2);
        stepSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ step=Double.parseDouble(steps[pos]); refresh(); }});
        String[] lev={"3","6","9","12"}; Spinner levelSpin=new Spinner(this); levelSpin.setAdapter(spinnerAdapter(lev)); levelSpin.setSelection(2);
        levelSpin.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ levels=Integer.parseInt(lev[pos]); refresh(); }});
        selectors.addView(tv("Шаг",14,MUTED,true)); selectors.addView(stepSpin,new LinearLayout.LayoutParams(0,dp(48),1)); selectors.addView(tv("Уровни",14,MUTED,true)); selectors.addView(levelSpin,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(selectors);
        LinearLayout dc=card(); dc.addView(tv("ASKS",14,RED,true)); asksBox=new LinearLayout(this); asksBox.setOrientation(LinearLayout.VERTICAL); dc.addView(asksBox); domBiasText=tv("DOM —",15,MUTED,true); dc.addView(domBiasText); bidsBox=new LinearLayout(this); bidsBox.setOrientation(LinearLayout.VERTICAL); dc.addView(bidsBox); dc.addView(tv("BIDS",14,GREEN,true)); root.addView(dc);
        root.addView(tv("Полоса = относительный объём уровня · ярче = top-3 стенка",13,MUTED,false)); return sv;
    }

    private View contextPage(){
        ScrollView sv=pageScroll(); LinearLayout root=pageRoot(); sv.addView(root); root.addView(section("КОНТЕКСТ ТАЙМФРЕЙМА"));
        LinearLayout candle=card(); ctxPrice=tv("Свеча —",18,TEXT,true); ctxRange=tv("High / Low —",15,MUTED,false); ctxVolume=tv("Volume —",15,MUTED,false); candle.addView(ctxPrice); candle.addView(ctxRange); candle.addView(ctxVolume); ctxPriceChart=new SparkView(this,BLUE); candle.addView(ctxPriceChart,new LinearLayout.LayoutParams(-1,dp(125))); root.addView(candle);
        LinearLayout flow=card(); ctxTaker=tv("Taker —",17,TEXT,true); ctxOi=tv("OI —",17,TEXT,true); ctxRatio=tv("Long/Short —",17,TEXT,true); flow.addView(ctxTaker); flow.addView(ctxOi); flow.addView(ctxRatio); root.addView(flow);
        LinearLayout summary=card(); summary.setBackgroundColor(CARD2); ctxSummary=tv("Сводка —",16,TEXT,true); summary.addView(ctxSummary); root.addView(summary); return sv;
    }

    private void changePage(int delta){ int cur=pager.getDisplayedChild(), next=cur+delta; if(next<0||next>=pager.getChildCount())return; boolean forward=delta>0; int w=getResources().getDisplayMetrics().widthPixels; Animation in=new TranslateAnimation(forward?w:-w,0,0,0); in.setDuration(180); Animation out=new TranslateAnimation(0,forward?-w:w,0,0); out.setDuration(180); pager.setInAnimation(in); pager.setOutAnimation(out); pager.setDisplayedChild(next); updateDots(); }
    private void goPage(int target){ int cur=pager.getDisplayedChild(); if(cur==target)return; while(cur<target){ changePage(1); cur++; } while(cur>target){ changePage(-1); cur--; } }
    private void updateDots(){ dots.removeAllViews(); int cur=pager==null?1:pager.getDisplayedChild(); for(int i=0;i<3;i++){ final int idx=i; TextView d=tv(i==cur?"●":"○",17,i==cur?BLUE:MUTED,true); d.setGravity(Gravity.CENTER); d.setPadding(dp(10),0,dp(10),0); d.setOnClickListener(v->goPage(idx)); dots.addView(d,new LinearLayout.LayoutParams(dp(42),dp(32))); } }
    private void updateTfButtons(){ for(Map.Entry<String,Button> e:tfButtons.entrySet()){ boolean on=e.getKey().equals(tf); e.getValue().setTextColor(on?BLUE:TEXT); e.getValue().setTypeface(Typeface.DEFAULT,on?Typeface.BOLD:Typeface.NORMAL); } }

    private TextView tv(String s,float sp,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(8),dp(7),dp(8),dp(7)); if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v; }
    private ScrollView pageScroll(){ ScrollView s=new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(BG); return s; }
    private LinearLayout pageRoot(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,0,0,dp(14)); return l; }
    private LinearLayout card(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(8),dp(8),dp(8),dp(8)); l.setBackgroundColor(CARD); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(4),0,dp(5)); l.setLayoutParams(p); return l; }
    private LinearLayout metricCard(){ LinearLayout l=card(); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(2),dp(4),dp(2),dp(5)); l.setLayoutParams(p); return l; }
    private TextView section(String s){ TextView v=tv(s,14,MUTED,true); v.setPadding(dp(4),dp(8),0,dp(3)); return v; }
    private Button smallButton(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(16); b.setAllCaps(false); b.setBackgroundColor(CARD); return b; }
    private ArrayAdapter<String> spinnerAdapter(String[] x){ return new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,x); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void refresh(){ if(!running)return; io.execute(()->{ try{ Snapshot s=load(); ui.post(()->render(s)); } catch(Exception e){ ui.post(()->{ if(statusText!=null)statusText.setText("Ошибка сети: "+e.getClass().getSimpleName()); }); } finally{ if(running)ui.postDelayed(this::refresh,1500); } }); }

    private Snapshot load() throws Exception{
        Snapshot s=new Snapshot(); JSONObject ticker=new JSONObject(get("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=BNBUSDT")); s.price=ticker.getDouble("lastPrice"); s.change=ticker.getDouble("priceChangePercent"); JSONObject oi=new JSONObject(get("https://fapi.binance.com/fapi/v1/openInterest?symbol=BNBUSDT")); s.oi=oi.getDouble("openInterest");
        JSONArray oih=new JSONArray(get("https://fapi.binance.com/futures/data/openInterestHist?symbol=BNBUSDT&period="+tf+"&limit=24")); s.oiSeries=new double[oih.length()]; for(int i=0;i<oih.length();i++)s.oiSeries[i]=oih.getJSONObject(i).getDouble("sumOpenInterest"); if(oih.length()>=2){ double a=s.oiSeries[oih.length()-2], b=s.oiSeries[oih.length()-1]; s.oiDelta=a==0?0:(b-a)/a*100.0; }
        JSONArray tak=new JSONArray(get("https://fapi.binance.com/futures/data/takerlongshortRatio?symbol=BNBUSDT&period="+tf+"&limit=1")); if(tak.length()>0){ JSONObject t=tak.getJSONObject(0); s.buyVol=t.getDouble("buyVol"); s.sellVol=t.getDouble("sellVol"); }
        JSONArray ls=new JSONArray(get("https://fapi.binance.com/futures/data/globalLongShortAccountRatio?symbol=BNBUSDT&period="+tf+"&limit=1")); if(ls.length()>0)s.ls=ls.getJSONObject(0).getDouble("longShortRatio");
        JSONArray kl=new JSONArray(get("https://fapi.binance.com/fapi/v1/klines?symbol=BNBUSDT&interval="+tf+"&limit=48")); s.priceSeries=new double[kl.length()]; for(int i=0;i<kl.length();i++){ JSONArray k=kl.getJSONArray(i); s.priceSeries[i]=Double.parseDouble(k.getString(4)); if(i==kl.length()-1){ s.open=Double.parseDouble(k.getString(1)); s.high=Double.parseDouble(k.getString(2)); s.low=Double.parseDouble(k.getString(3)); s.close=Double.parseDouble(k.getString(4)); s.volume=Double.parseDouble(k.getString(5)); }}
        JSONObject depth=new JSONObject(get("https://fapi.binance.com/fapi/v1/depth?symbol=BNBUSDT&limit=1000")); s.asks=aggregate(depth.getJSONArray("asks"),true,s.price); s.bids=aggregate(depth.getJSONArray("bids"),false,s.price); return s;
    }

    private List<Level> aggregate(JSONArray arr,boolean ask,double price)throws Exception{ TreeMap<Double,Double> map=new TreeMap<>(); for(int i=0;i<arr.length();i++){ JSONArray r=arr.getJSONArray(i); double p=Double.parseDouble(r.getString(0)), q=Double.parseDouble(r.getString(1)); double bucket=ask?Math.ceil(p/step)*step:Math.floor(p/step)*step; map.put(bucket,map.getOrDefault(bucket,0.0)+q); } List<Level> out=new ArrayList<>(); NavigableMap<Double,Double> nav=ask?map.tailMap(price,true):map.headMap(price,true).descendingMap(); int n=0; for(Map.Entry<Double,Double> e:nav.entrySet()){ if(n++>=levels)break; out.add(new Level(e.getKey(),e.getValue())); } return out; }

    private void render(Snapshot s){
        priceText.setText(p2.format(s.price)); priceText.setTextColor(s.change>=0?GREEN:RED); changeText.setText("BNBUSDT PERP   "+(s.change>=0?"+":"")+p2.format(s.change)+"%   ·   "+tfLabel());
        double total=s.buyVol+s.sellVol, buyPct=total==0?0:s.buyVol/total*100; oiText.setText("OI  "+compact(s.oi)+" BNB"); oiDeltaText.setText("Δ "+(s.oiDelta>=0?"+":"")+p2.format(s.oiDelta)+"%"); oiDeltaText.setTextColor(s.oiDelta>=0?GREEN:RED); takerText.setText("Taker  "+p2.format(buyPct)+"% BUY"); takerText.setTextColor(buyPct>=50?GREEN:RED); ratioText.setText("Long/Short  "+p2.format(s.ls));
        double bq=sum(s.bids), aq=sum(s.asks), dom=aq+bq==0?50:bq/(aq+bq)*100; domBiasText.setText("DOM  bids "+p2.format(dom)+"%   ·   asks "+p2.format(100-dom)+"%"); domBiasText.setTextColor(dom>=50?GREEN:RED); renderLevels(asksBox,s.asks,true); renderLevels(bidsBox,s.bids,false);
        priceChart.setData(s.priceSeries); oiChart.setData(s.oiSeries); ctxPriceChart.setData(s.priceSeries); if(statusText!=null)statusText.setText("LIVE · "+tfLabel()+" · обновление ~1.5 сек");
        double candlePct=s.open==0?0:(s.close-s.open)/s.open*100; ctxPrice.setText(tfLabel()+" свеча  "+p2.format(s.open)+" → "+p2.format(s.close)+"  ("+(candlePct>=0?"+":"")+p2.format(candlePct)+"%)"); ctxPrice.setTextColor(candlePct>=0?GREEN:RED); ctxRange.setText("High "+p2.format(s.high)+"   ·   Low "+p2.format(s.low)+"   ·   Range "+p2.format(s.high-s.low)); ctxVolume.setText("Volume  "+compact(s.volume)+" BNB"); ctxTaker.setText("Taker:  BUY "+p2.format(buyPct)+"%   /   SELL "+p2.format(100-buyPct)+"%"); ctxTaker.setTextColor(buyPct>=50?GREEN:RED); ctxOi.setText("OI: "+compact(s.oi)+" BNB   ·   Δ "+(s.oiDelta>=0?"+":"")+p2.format(s.oiDelta)+"%"); ctxRatio.setText("Long/Short: "+p2.format(s.ls)); ctxSummary.setText(summaryText(candlePct,buyPct,s.oiDelta,dom));
    }

    private void renderLevels(LinearLayout box,List<Level> ls,boolean ask){
        box.removeAllViews(); if(ls==null||ls.isEmpty())return; List<Level> rank=new ArrayList<>(ls); rank.sort((a,b)->Double.compare(b.qty,a.qty)); Set<Double> top=new HashSet<>(); for(int i=0;i<Math.min(3,rank.size());i++)top.add(rank.get(i).price); double max=rank.get(0).qty; List<Level> draw=new ArrayList<>(ls); if(ask)Collections.reverse(draw); Set<String> now=new HashSet<>();
        for(Level l:draw){ String k=(ask?"A":"B")+":"+p2.format(l.price); now.add(k); int h=holdCounts.getOrDefault(k,0)+1; holdCounts.put(k,h); FrameLayout frame=new FrameLayout(this); frame.setMinimumHeight(dp(48)); View bar=new View(this); int base=ask?Color.rgb(43,22,29):Color.rgb(18,44,35); int hot=ask?Color.rgb(80,28,38):Color.rgb(20,70,48); bar.setBackgroundColor(top.contains(l.price)?hot:base); frame.addView(bar,new FrameLayout.LayoutParams((int)(dp(300)*Math.max(0.10,l.qty/max)),-1)); LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView p=tv(p2.format(l.price),16,ask?RED:GREEN,true); TextView q=tv(q1.format(l.qty)+" BNB"+(h>=3?"  ×"+h:""),15,TEXT,top.contains(l.price)); row.addView(p,new LinearLayout.LayoutParams(0,dp(48),1)); row.addView(q,new LinearLayout.LayoutParams(0,dp(48),1)); frame.addView(row,new FrameLayout.LayoutParams(-1,-1)); box.addView(frame,new LinearLayout.LayoutParams(-1,dp(48))); View divider=new View(this); divider.setBackgroundColor(LINE); box.addView(divider,new LinearLayout.LayoutParams(-1,1)); }
        holdCounts.keySet().removeIf(k->k.startsWith(ask?"A:":"B:")&&!now.contains(k));
    }

    private String summaryText(double candlePct,double buyPct,double oiDelta,double dom){ int bull=0,bear=0; if(candlePct>0)bull++; else if(candlePct<0)bear++; if(buyPct>52)bull++; else if(buyPct<48)bear++; if(dom>52)bull++; else if(dom<48)bear++; String tone=bull>bear?"Покупатели сильнее":bear>bull?"Продавцы сильнее":"Баланс"; String oi=oiDelta>0?"OI растёт — позиции добавляются":oiDelta<0?"OI снижается — позиции сокращаются":"OI почти без изменения"; return tone+"\n"+oi+"\nНе сигнал на вход: смотри реакцию цены у уровней."; }
    private String tfLabel(){ return "15m".equals(tf)?"M15":"1h".equals(tf)?"H1":"H4"; }
    private double sum(List<Level> ls){ double x=0; if(ls!=null)for(Level l:ls)x+=l.qty; return x; }
    private String compact(double v){ if(v>=1_000_000)return p2.format(v/1_000_000)+"M"; if(v>=1000)return p2.format(v/1000)+"K"; return p2.format(v); }
    private String get(String u)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(7000); c.setReadTimeout(7000); c.setRequestProperty("User-Agent","BNB-Flow-Lens-Android/1.3"); int code=c.getResponseCode(); if(code!=200)throw new RuntimeException("HTTP "+code); BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); r.close(); c.disconnect(); return b.toString(); }

    static class Level{ double price,qty; Level(double p,double q){price=p;qty=q;} }
    static class Snapshot{ double price,change,oi,oiDelta,buyVol,sellVol,ls,open,high,low,close,volume; double[] priceSeries,oiSeries; List<Level> asks,bids; }

    class SparkView extends View{
        private double[] data=new double[0]; private final Paint grid=new Paint(1), linePaint=new Paint(1);
        SparkView(Activity c,int color){ super(c); grid.setColor(LINE); grid.setStrokeWidth(dp(1)); linePaint.setColor(color); linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeWidth(dp(2)); setBackgroundColor(CARD); }
        void setData(double[] d){ data=d==null?new double[0]:d.clone(); invalidate(); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); int w=getWidth(),h=getHeight(); if(w<=0||h<=0)return; for(int i=1;i<4;i++)c.drawLine(0,h*i/4f,w,h*i/4f,grid); if(data.length<2)return; double min=Double.MAX_VALUE,max=-Double.MAX_VALUE; for(double v:data){ if(v<min)min=v; if(v>max)max=v; } if(max<=min)max=min+1; Path p=new Path(); for(int i=0;i<data.length;i++){ float x=(float)i/(data.length-1)*(w-dp(8))+dp(4); float y=(float)((max-data[i])/(max-min))*(h-dp(16))+dp(8); if(i==0)p.moveTo(x,y); else p.lineTo(x,y); } c.drawPath(p,linePaint); }
    }
}
