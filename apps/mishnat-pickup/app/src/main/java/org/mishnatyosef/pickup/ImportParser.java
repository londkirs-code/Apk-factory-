package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.*;
import java.io.FilterInputStream;
import java.io.File;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Strict, offline-only parser for Mishnat Pickup List v1 files. */
final class ImportParser {
    static final String FORMAT="mishnat-pickup-list";
    static final int VERSION=1,MAX_ENTRIES=3000,MAX_LISTS=20,MAX_ITEMS=5000;
    static final long MAX_JSON=10L*1024L*1024L,MAX_ZIP=100L*1024L*1024L,MAX_EXPANDED=300L*1024L*1024L;
    static final class Item {String id,name,quantity,unit,note,category,image,imageUrl;}
    static final class ImportedList {String accountId,accountName,orderId,orderName,pickupDate,updatedAt;final List<Item> items=new ArrayList<>();}
    static final class Bundle {final List<ImportedList> lists=new ArrayList<>();final File workDir;Bundle(File d){workDir=d;}File imageFile(String p){return p==null||p.isEmpty()?null:new File(workDir,p);}}
    private ImportParser(){}
    static Bundle parse(InputStream source,File workDir)throws Exception {
        if(source==null)throw new Exception("לא ניתן לפתוח את הקובץ.");
        if(workDir.exists())delete(workDir);if(!workDir.mkdirs())throw new Exception("לא ניתן ליצור תיקיית ייבוא זמנית.");
        BufferedInputStream in=new BufferedInputStream(source);in.mark(8);int a=in.read(),b=in.read();in.reset();
        try {
            byte[] raw=(a=='P'&&b=='K')
                    ? readZip(new LimitInputStream(in,MAX_ZIP),workDir)
                    : readLimited(in,MAX_JSON,"קובץ גדול מ־10MB.");
            int first=0;
            if(raw.length>=3&&(raw[0]&0xff)==0xef&&(raw[1]&0xff)==0xbb&&(raw[2]&0xff)==0xbf)first=3;
            while(first<raw.length&&(raw[first]==' '||raw[first]==10||raw[first]==13||raw[first]==9))first++;
            if(first<raw.length&&raw[first]=='{')return parseJson(raw,workDir);
            return parseText(raw,workDir);
        } catch(Exception e){delete(workDir);throw e;}
        finally{try{in.close();}catch(Exception ignored){}}
    }
    /**
     * Plain text format: one product per line. Optional columns are separated by |
     * name | quantity | unit | note | category. A line with only a name defaults to quantity 1.
     * Empty lines and lines beginning with # are ignored, except "## name", which starts a new list
     * (so one text file can hold several lists, as the app's export writes them).
     */
    private static Bundle parseText(byte[] raw,File dir)throws Exception {
        String text=new String(raw,java.nio.charset.StandardCharsets.UTF_8);
        if(text.length()>0&&text.charAt(0)==0xFEFF)text=text.substring(1);
        String[] lines=text.split("\\r?\\n");
        List<ImportedList> lists=new ArrayList<>();
        ImportedList list=newTextList("רשימה מקובץ טקסט",0);
        Set<String> ids=new HashSet<>();
        Map<ImportedList,Set<String>> idsByList=new HashMap<>();
        idsByList.put(list,ids);
        String currentCategory="";
        int total=0;
        for(int i=0;i<lines.length;i++) {
            String line=lines[i]==null?"":lines[i].trim();
            if(line.startsWith("### ")) {
                // קטגוריה למוצרים שאחריה (פורמט קומפקטי, למשל מקוד QR). "-" = בלי קטגוריה.
                String category=clean(line.substring(4));
                currentCategory="-".equals(category)?"":(category.length()>100?category.substring(0,100):category);
                continue;
            }
            if(line.startsWith("## ")) {
                // רשימה חדשה באותו קובץ. רשימה באותו שם ממשיכה את הקודמת (למשל כמה קודי QR של אותה הזמנה).
                String name=clean(line.substring(3));
                if(name.length()>200)name=name.substring(0,200);
                if(name.isEmpty())name="רשימה מקובץ טקסט";
                currentCategory="";
                if(name.equals(list.orderName))continue;
                ImportedList existing=null;
                for(ImportedList previous:lists)if(name.equals(previous.orderName)){existing=previous;break;}
                if(!list.items.isEmpty()&&!lists.contains(list))lists.add(list);
                if(existing!=null){list=existing;ids=idsByList.get(existing);continue;}
                if(lists.size()>=MAX_LISTS)throw new Exception("הקובץ חייב להכיל בין 1 ל־20 רשימות.");
                list=newTextList(name,lists.size());
                ids=new HashSet<>();
                idsByList.put(list,ids);
                continue;
            }
            if(line.isEmpty()||line.startsWith("#"))continue;
            if(++total>MAX_ITEMS)throw new Exception("קובץ הטקסט מכיל יותר מ־5000 פריטים.");
            if(list.items.size()>=MAX_ITEMS)throw new Exception("קובץ הטקסט מכיל יותר מ־5000 פריטים.");
            String[] columns=line.split("\\|",-1);
            String name=clean(columns[0]);
            if(name.isEmpty())continue;
            if(name.length()>200)throw new Exception("שורה "+(i+1)+": שם מוצר ארוך מ־200 תווים.");
            String quantity="1",unit="",note="",category="";
            if(columns.length>1&&!clean(columns[1]).isEmpty()) {
                quantity=clean(columns[1]);
                try {
                    double amount=Double.parseDouble(quantity.replace(',','.'));
                    if(Double.isNaN(amount)||Double.isInfinite(amount)||amount<=0)throw new NumberFormatException();
                    // "3" נשאר "3" ולא הופך ל-"3.0" בתצוגה.
                    quantity=amount==Math.rint(amount)&&amount<1e15
                            ?String.valueOf((long)amount)
                            :java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
                } catch(Exception e) { throw new Exception("שורה "+(i+1)+": הכמות חייבת להיות מספר חיובי."); }
            }
            if(columns.length>2)unit=clean(columns[2]);
            if(columns.length>3)note=clean(columns[3]);
            if(columns.length>4)category=clean(columns[4]);
            if(category.isEmpty())category=currentCategory;
            if(columns.length>5)throw new Exception("שורה "+(i+1)+": אפשר לכלול עד חמש עמודות.");
            if(unit.length()>40||note.length()>300||category.length()>100)
                throw new Exception("שורה "+(i+1)+": אחד משדות הפריט ארוך מהמותר.");
            String base=DataGuard.sha256(name.toLowerCase(Locale.ROOT));
            String id=base.substring(0,24);
            int suffix=1;while(!ids.add(id))id=base.substring(0,20)+"_"+(suffix++);
            Item item=new Item();item.id=id;item.name=name;item.quantity=quantity;
            item.unit=unit;item.note=note;item.category=category;
            list.items.add(item);
        }
        if(!list.items.isEmpty()&&!lists.contains(list))lists.add(list);
        if(lists.isEmpty())throw new Exception("קובץ הטקסט אינו מכיל פריטים. כל שורה צריכה לכלול שם מוצר.");
        Bundle bundle=new Bundle(dir);bundle.lists.addAll(lists);return bundle;
    }

    private static ImportedList newTextList(String name,int index) {
        ImportedList list=new ImportedList();
        list.accountId="text-"+java.util.UUID.randomUUID().toString();
        list.accountName="רשימה מקובץ טקסט";
        list.orderId=String.valueOf(System.currentTimeMillis()+index);
        list.orderName=name;
        return list;
    }

    private static byte[] readZip(InputStream input,File workDir)throws Exception {
        ByteArrayOutputStream json=new ByteArrayOutputStream();ZipInputStream zip=new ZipInputStream(input);
        long expanded=0;int entries=0;boolean found=false;Set<String> names=new HashSet<>();byte[] buf=new byte[8192];
        try {ZipEntry e;while((e=zip.getNextEntry())!=null){
            if(++entries>MAX_ENTRIES)throw new Exception("קובץ ZIP מכיל יותר מ־3000 קבצים.");
            String name=e.getName();
            if(name==null||name.startsWith("/")||name.startsWith("\\")||name.contains("\\")||name.contains("../")||name.equals("..")||name.contains("/../"))throw new Exception("קובץ ZIP מכיל נתיב לא בטוח.");
            if(e.isDirectory()){if(!"images/".equals(name))throw new Exception("תיקייה לא מוכרת בקובץ ZIP.");continue;}
            if(!names.add(name))throw new Exception("קובץ כפול בתוך ZIP: "+name);
            if("list.json".equals(name)){
                if(found)throw new Exception("קובץ list.json כפול.");found=true;int n;
                while((n=zip.read(buf))!=-1){if(json.size()+n>MAX_JSON)throw new Exception("קובץ list.json גדול מ־10MB.");json.write(buf,0,n);expanded+=n;}
            } else if(name.startsWith("images/")){
                String base=name.substring(7);if(!validImageName(base))throw new Exception("שם תמונה לא תקין בקובץ ZIP: "+base);
                File dir=new File(workDir,"images"),dest=new File(dir,base);String root=dir.getCanonicalPath()+File.separator;
                if(!dest.getCanonicalPath().startsWith(root))throw new Exception("נתיב תמונה חורג מתיקיית הייבוא.");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("לא ניתן ליצור תיקיית תמונות.");
                FileOutputStream out=new FileOutputStream(dest);long written=0;
                try{int n;while((n=zip.read(buf))!=-1){written+=n;expanded+=n;if(expanded>MAX_EXPANDED)throw new Exception("תוכן ZIP גדול מ־300MB לאחר פריסה.");out.write(buf,0,n);}out.getFD().sync();}
                finally{out.close();}
            } else throw new Exception("קובץ לא מוכר בתוך ZIP: "+name);
            if(expanded>MAX_EXPANDED)throw new Exception("תוכן ZIP גדול מ־300MB לאחר פריסה.");zip.closeEntry();
        }}finally{try{zip.close();}catch(Exception ignored){}}
        if(!found)throw new Exception("בקובץ ZIP חסר list.json בתיקייה הראשית.");return json.toByteArray();
    }
    private static byte[] readLimited(InputStream in,long max,String error)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(o.size()+n>max)throw new Exception(error);o.write(b,0,n);}return o.toByteArray();}
    private static Bundle parseJson(byte[] raw,File dir)throws Exception{
        String text=new String(raw,java.nio.charset.StandardCharsets.UTF_8);
        if(text.length()>0&&text.charAt(0)==0xFEFF)text=text.substring(1);
        Object parsed;try{JSONTokener t=new JSONTokener(text);parsed=t.nextValue();if(t.nextClean()!=0)throw new Exception("יש תווים מיותרים אחרי ה־JSON.");}catch(Exception e){throw new Exception("קובץ JSON לא תקין: "+e.getMessage());}
        if(!(parsed instanceof JSONObject))throw new Exception("מבנה הקובץ אינו אובייקט JSON.");
        JSONObject root=(JSONObject)parsed;if(!FORMAT.equals(clean(root.optString("format",""))))throw new Exception("סוג הקובץ אינו mishnat-pickup-list.");
        Object versionValue=root.opt("version");if(!(versionValue instanceof Number))throw new Exception("version חייב להיות מספר שלם.");
        double versionNumber=((Number)versionValue).doubleValue();if(versionNumber!=Math.rint(versionNumber))throw new Exception("version חייב להיות מספר שלם.");
        int version=(int)versionNumber;if(version>VERSION)throw new Exception("הקובץ נוצר בגרסה חדשה יותר. עדכן את האפליקציה.");if(version<1)throw new Exception("גרסת קובץ לא תקינה.");
        JSONArray lists=root.optJSONArray("lists");if(lists==null||lists.length()<1||lists.length()>MAX_LISTS)throw new Exception("הקובץ חייב להכיל בין 1 ל־20 רשימות.");
        Bundle bundle=new Bundle(dir);Set<String> accountIds=new HashSet<>();
        for(int i=0;i<lists.length();i++){int ln=i+1;JSONObject l=lists.optJSONObject(i);if(l==null)throw new Exception("רשימה "+ln+": מבנה לא תקין.");
            JSONObject account=l.optJSONObject("account"),order=l.optJSONObject("order");if(account==null)throw new Exception("רשימה "+ln+": חסר account.");if(order==null)throw new Exception("רשימה "+ln+": חסר order.");
            ImportedList list=new ImportedList();list.accountId=required(account,"id","רשימה "+ln+", account");if(!accountIds.add(list.accountId))throw new Exception("רשימה "+ln+": account.id כפול בקובץ.");
            list.accountName=optional(account,"name",200);list.orderId=required(order,"id","רשימה "+ln+", order");list.orderName=optional(order,"name",200);list.pickupDate=optional(order,"pickupDate",40);list.updatedAt=optional(order,"updatedAt",80);
            JSONArray items=l.optJSONArray("items");if(items==null||items.length()>MAX_ITEMS)throw new Exception("רשימה "+ln+": items חייב להכיל עד 5000 מוצרים.");Set<String> ids=new HashSet<>();
            for(int j=0;j<items.length();j++){int ix=j+1;JSONObject o=items.optJSONObject(j);if(o==null)throw new Exception("רשימה "+ln+", מוצר "+ix+": מבנה לא תקין.");Item item=new Item();
                item.id=required(o,"id","רשימה "+ln+", מוצר "+ix);if(!ids.add(item.id))throw new Exception("רשימה "+ln+", מוצר "+ix+": מזהה מוצר כפול.");
                item.name=required(o,"name","רשימה "+ln+", מוצר "+ix);if(item.name.length()>200)throw new Exception("רשימה "+ln+", מוצר "+ix+": name ארוך מ־200 תווים.");
                Object q=o.opt("quantity");if(!(q instanceof Number))throw new Exception("רשימה "+ln+", מוצר "+ix+": חסר או לא תקין quantity.");double n=((Number)q).doubleValue();
                if(Double.isNaN(n)||Double.isInfinite(n)||n<=0)throw new Exception("רשימה "+ln+", מוצר "+ix+": quantity חייב להיות מספר חיובי.");
                item.quantity=n==Math.rint(n)&&n<1e15?String.valueOf((long)n):java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();item.unit=optional(o,"unit",40);item.note=optional(o,"note",300);item.category=optional(o,"category",100);item.image=optional(o,"image",160);item.imageUrl=optional(o,"imageUrl",2048);
                if(!item.image.isEmpty()&&!validImagePath(item.image))throw new Exception("רשימה "+ln+", מוצר "+ix+": נתיב תמונה לא בטוח.");
                if(!item.imageUrl.isEmpty()&&!item.imageUrl.toLowerCase(Locale.ROOT).startsWith("https://"))throw new Exception("רשימה "+ln+", מוצר "+ix+": imageUrl חייב להתחיל ב־https://.");
                list.items.add(item);
            }bundle.lists.add(list);
        }return bundle;
    }
    private static String required(JSONObject o,String k,String where)throws Exception{Object raw=o.opt(k);if(!(raw instanceof String))throw new Exception(where+": "+k+" חייב להיות טקסט.");String v=clean((String)raw);if(v.isEmpty())throw new Exception(where+": חסר "+k+".");if(v.length()>512)throw new Exception(where+": "+k+" ארוך מדי.");return v;}
    private static String optional(JSONObject o,String k,int max)throws Exception{Object raw=o.opt(k);if(raw==null||raw==JSONObject.NULL)return "";if(!(raw instanceof String))throw new Exception(k+" חייב להיות טקסט.");String v=clean((String)raw);if(v.length()>max)throw new Exception(k+" ארוך מהמותר ("+max+" תווים).");StringBuilder b=new StringBuilder();for(int i=0;i<v.length();i++){char c=v.charAt(i);if(c>=32&&c!=127)b.append(c);}return b.toString();}
    private static String clean(String s){return s==null?"":s.replace("\r","").trim();}
    private static boolean validImagePath(String p){return p!=null&&p.startsWith("images/")&&!p.startsWith("/")&&!p.contains("..")&&!p.contains("\\")&&p.indexOf(':')<0&&validImageName(p.substring(7));}
    private static boolean validImageName(String n){if(n==null||n.length()<5||n.length()>100||!n.matches("[A-Za-z0-9._-]+"))return false;String x=n.toLowerCase(Locale.ROOT);return x.endsWith(".jpg")||x.endsWith(".jpeg")||x.endsWith(".png")||x.endsWith(".webp");}
    static void delete(File f){if(f==null||!f.exists())return;File[] c=f.listFiles();if(c!=null)for(File x:c)delete(x);f.delete();}
    private static final class LimitInputStream extends FilterInputStream {
        private final long max;private long count;LimitInputStream(InputStream in,long max){super(in);this.max=max;}
        @Override public int read()throws IOException{int v=super.read();if(v!=-1&&++count>max)throw new IOException("קובץ ZIP גדול מ־100MB.");return v;}
        @Override public int read(byte[] b,int off,int len)throws IOException{int n=super.read(b,off,len);if(n>0){count+=n;if(count>max)throw new IOException("קובץ ZIP גדול מ־100MB.");}return n;}
    }
}
