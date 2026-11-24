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

    static String title(JSONObject order) {
        if(order==null)return "הזמנה";
        String direct=first(order,"order_name","name","title");
        JSONObject sale=order.optJSONObject("sale");
        JSONObject parent=order.optJSONObject("parent");
        String week=first(sale,"week","name","title");
        if(week.isEmpty())week=first(parent,"week","name","title");
        if(week.isEmpty())week=first(order,"week","parasha","parasha_name","sale_name");
        if(!week.isEmpty())return week;
        if(!direct.isEmpty())return direct;
        String id=first(order,"id","order_id","orderID");
        return id.isEmpty()?"הזמנה מההיסטוריה":"הזמנה "+id;
    }

    static boolean isCancelled(JSONObject order) {
        String status=first(order,"status","order_status","state").toLowerCase(java.util.Locale.ROOT);
        return status.contains("cancel")||status.contains("בוטל")
                ||status.contains("rejected")||status.contains("deleted");
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
            String value=object.optString(key,"").trim();
            if(!value.isEmpty()&&!"null".equalsIgnoreCase(value))return value;
        }
        return "";
    }
}
