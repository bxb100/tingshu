package android.webkit;

public abstract class CookieManager {
    public static CookieManager getInstance() {
        throw new RuntimeException("Stub!");
    }

    public abstract void setCookie(String url, String value);
}
