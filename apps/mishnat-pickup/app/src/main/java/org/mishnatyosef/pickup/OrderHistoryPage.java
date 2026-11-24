package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Parses the paginated response returned by GET /api/orders?page=N.
 * Kept separate from Activity/UI so pagination and API envelopes can be tested independently.
 */
final class OrderHistoryPage {
    final JSONArray orders;
    final int currentPage;
    final int lastPage;
    final int perPage;
    final String nextPageUrl;

    private OrderHistoryPage(JSONArray rows,int current,int last,int perPage,String next) {
        this.orders=rows==null?new JSONArray():rows;
        this.currentPage=Math.max(1,current);
        this.lastPage=Math.max(this.currentPage,last);
        this.perPage=Math.max(0,perPage);
        this.nextPageUrl=next==null?"":next;
    }

    static OrderHistoryPage parse(String response,int requestedPage)throws Exception {
        Object parsed=new JSONTokener(response==null?"":response).nextValue();
        if(parsed instanceof JSONArray)
            return new OrderHistoryPage((JSONArray)parsed,requestedPage,requestedPage,0,"");
        if(!(parsed instanceof JSONObject))
            throw new Exception("תשובת היסטוריית ההזמנות אינה תקינה.");
        JSONObject root=(JSONObject)parsed;
        JSONObject dataObject=root.optJSONObject("data");
        JSONArray rows=root.optJSONArray("data");
        if(rows==null)rows=root.optJSONArray("orders");
        if(rows==null)rows=root.optJSONArray("items");
        if(rows==null&&dataObject!=null) {
            rows=dataObject.optJSONArray("orders");
            if(rows==null)rows=dataObject.optJSONArray("items");
            if(rows==null)rows=dataObject.optJSONArray("data");
        }
        if(rows==null)throw new Exception("השרת החזיר היסטוריה במבנה לא מוכר.");

        JSONObject meta=root.optJSONObject("meta");
        if(meta==null)meta=root.optJSONObject("pagination");
        if(meta==null&&dataObject!=null)meta=dataObject.optJSONObject("meta");
        int current=number(root,"current_page",number(meta,"current_page",requestedPage));
        int last=number(root,"last_page",number(meta,"last_page",current));
        int per=number(root,"per_page",number(meta,"per_page",rows.length()));
        String next=string(root,"next_page_url",string(meta,"next_page_url",""));
        if(next.isEmpty()&&dataObject!=null)next=string(dataObject,"next_page_url","");
        if(last<current)last=current;
        if(last==current&&!next.isEmpty())last=current+1;
        return new OrderHistoryPage(rows,current,last,per,next);
    }

    boolean hasMore() { return currentPage<lastPage||!nextPageUrl.isEmpty(); }

    /**
     * שם המכירה (הפרשה) של הזמנה, לפי סדר עדיפות קבוע. הערך נלקח מנתוני ההזמנה עצמם
     * ולעולם אינו מחושב לפי תאריך. נתמך גם מבנה מקונן ({order:{...}} או {data:{...}}).
     * מחזיר מחרוזת ריקה כשאין שם.
     */
    static String saleName(JSONObject order) {
        JSONObject[] candidates=candidates(order);
        // 1. parent.week הוא השדה הראשי לשם המוצג, ולכן נבדק ראשון בכל רמות הקינון.
        String[][] nestedSteps={{"parent","week"},{"sale","week"},{"sale","name"},{"sale","title"},
                {"parent","name"},{"parent","title"}};
        for(String[] step:nestedSteps)
            for(JSONObject candidate:candidates) {
                String value=first(candidate==null?null:candidate.optJSONObject(step[0]),step[1]);
                if(!value.isEmpty())return value;
            }
        String[] directKeys={"week","parasha","parasha_name","sale_name","order_name","name","title"};
        for(String key:directKeys)
            for(JSONObject candidate:candidates) {
                String value=first(candidate,key);
                if(!value.isEmpty())return value;
            }
        return "";
    }

    /** שם לתצוגה: שם המכירה, ואם אין - "הזמנה" ומזהה ההזמנה. */
    static String title(JSONObject order) {
        String name=saleName(order);
        if(!name.isEmpty())return name;
        String id="";
        for(JSONObject candidate:candidates(order)){id=first(candidate,"id","order_id","orderID");if(!id.isEmpty())break;}
        return id.isEmpty()?"הזמנה":"הזמנה "+id;
    }

    static boolean isCancelled(JSONObject order) {
        for(JSONObject candidate:candidates(order)) {
            if(candidate==null)continue;
            String status=first(candidate,"status","order_status","state","status_name").toLowerCase(java.util.Locale.ROOT);
            if(status.contains("cancel")||status.contains("בוטל")||status.contains("ביטול")
                    ||status.contains("rejected")||status.contains("deleted"))return true;
            JSONObject statusObject=candidate.optJSONObject("status");
            if(statusObject!=null){
                String nested=first(statusObject,"name","key","slug","title").toLowerCase(java.util.Locale.ROOT);
                if(nested.contains("cancel")||nested.contains("בוטל")||nested.contains("ביטול"))return true;
            }
            if(candidate.optBoolean("cancelled",false)||candidate.optBoolean("canceled",false)
                    ||candidate.optBoolean("is_cancelled",false)||candidate.optBoolean("is_canceled",false))return true;
            if(!first(candidate,"cancelled_at","canceled_at").isEmpty())return true;
        }
        return false;
    }

    /** ההזמנה עצמה ואחריה העטיפות המקוננות הנפוצות שלה. */
    private static JSONObject[] candidates(JSONObject order) {
        if(order==null)return new JSONObject[0];
        JSONObject inner=order.optJSONObject("order");
        JSONObject data=order.optJSONObject("data");
        JSONObject dataOrder=data==null?null:data.optJSONObject("order");
        return new JSONObject[]{order,inner,data,dataOrder};
    }

    private static int number(JSONObject object,String key,int fallback) {
        if(object==null)return fallback;
        Object value=object.opt(key);
        if(value instanceof Number)return ((Number)value).intValue();
        try{return Integer.parseInt(String.valueOf(value));}catch(Exception ignored){return fallback;}
    }
    private static String string(JSONObject object,String key,String fallback) {
        if(object==null)return fallback;
        String value=object.optString(key,fallback);
        return value==null||"null".equals(value)?fallback:value;
    }
    private static String first(JSONObject object,String... keys) {
        if(object==null)return "";
        for(String key:keys) {
            Object raw=object.opt(key);
            // ערך שהוא אובייקט או מערך אינו שם; מדלגים עליו במקום להציג JSON גולמי.
            if(raw==null||raw==JSONObject.NULL||raw instanceof JSONObject||raw instanceof JSONArray)continue;
            String value=String.valueOf(raw).trim();
            if(!value.isEmpty()&&!"null".equalsIgnoreCase(value))return value;
        }
        return "";
    }
}
