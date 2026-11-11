package io.github.harrythecake.cakewiki;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.harrythecake.cakewiki.net.WikiClient;
import io.github.harrythecake.cakewiki.net.WikiException;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One MCEF Modern browser per blocked host, kept for the game session: the player passes Cloudflare's check in it
 * ({@link McefVerifyScreen}), then CakeWiki's requests to that host run as {@code fetch()} inside the page, so they
 * are the verified browser's own requests (its cookies, its connection); nothing is copied out of the browser. The
 * bytes come back through a JCEF message router under CakeWiki's own JavaScript names ({@code cakewikiQuery}).
 * <p>
 * MCEF Modern has no getter for its JCEF client, so the browser starts on {@code about:blank}, the router goes onto
 * that browser's client, and only then does the browser go to the site (its page gets the query function).
 */
final class McefFetcher implements WikiClient.Transport {
	private static final Map<String, McefFetcher> BY_HOST = new ConcurrentHashMap<>();
	private static final Map<Long, Pending> PENDING = new ConcurrentHashMap<>();
	private static final AtomicLong IDS = new AtomicLong();
	private static CefMessageRouter router;
	private static CefClient routerClient;

	private record Pending(CompletableFuture<byte[]> future, String host) {}

	final String host;
	final MCEFBrowser browser;
	/** Where to go once the browser exists (it starts on about:blank). */
	private volatile String pendingUrl;

	private McefFetcher(MCEFApi api, String host, String url) {
		this.host = host;
		this.browser = api.createBrowser("about:blank", false);
		browser.resize(800, 600); // MCEF Modern wants a size right away; the screen sets the real one
		ensureRouter(browser.getCefBrowser().getClient());
		pendingUrl = url;
	}

	/** The browser for {@code host} (made the first time; later shown again where it is). */
	static synchronized McefFetcher forHost(MCEFApi api, String host, String url) {
		return BY_HOST.computeIfAbsent(host.toLowerCase(Locale.ROOT), h -> new McefFetcher(api, h, url));
	}

	/** Go to {@code url} as soon as the browser exists. */
	void navigate(String url) {
		pendingUrl = url;
		tick();
	}

	/** Sends a waiting navigation once the browser has been made (each client tick while the screen is open). */
	void tick() {
		String url = pendingUrl;
		if (url == null) return;
		String now = browser.getCefBrowser().getURL();
		if (now != null && !now.isEmpty()) {
			pendingUrl = null;
			browser.getCefBrowser().loadURL(url);
		}
	}

	/** Whether the browser is on its host now (fetch only works from the site's own pages). */
	boolean onHost() {
		try {
			String u = browser.getCefBrowser().getURL();
			return u != null && host.equalsIgnoreCase(URI.create(u).getHost());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public CompletableFuture<byte[]> get(URI uri) {
		if (!onHost()) {
			return CompletableFuture.failedFuture(WikiException.blocked(403, uri.getHost(), ""));
		}
		long id = IDS.incrementAndGet();
		CompletableFuture<byte[]> f = new CompletableFuture<>();
		PENDING.put(id, new Pending(f, uri.getHost()));
		CefBrowser cef = browser.getCefBrowser();
		cef.executeJavaScript(script(id, uri.toString()), cef.getURL(), 0);
		return f.orTimeout(30, TimeUnit.SECONDS).whenComplete((b, e) -> PENDING.remove(id));
	}

	/** fetch() in the page; the answer (status, Cloudflare's header, the body as base64) goes back to Java. */
	private static String script(long id, String url) {
		String quotedUrl = new com.google.gson.JsonPrimitive(url).toString(); // a JSON string is a valid JS string literal
		return "(function(){var id=" + id + ";function send(o){o.id=id;window.cakewikiQuery({request:JSON.stringify(o),"
				+ "onSuccess:function(){},onFailure:function(){}});}"
				// no WebP (Java can't read it; wiki.gg sends it when asked): skip the browser's cache, where the page's own
				// <img> loads left WebP copies, and ask for what CakeWiki decodes
				+ "fetch(" + quotedUrl + ",{credentials:'include',cache:'no-store',headers:{'Accept':'application/json,image/png,image/jpeg,image/gif;q=0.9,*/*;q=0.5'}}).then(function(r){return r.arrayBuffer().then(function(b){"
				+ "var a=new Uint8Array(b),s='',i;for(i=0;i<a.length;i+=32768){s+=String.fromCharCode.apply(null,a.subarray(i,i+32768));}"
				+ "send({status:r.status,cf:r.headers.get('cf-mitigated')||'',ray:r.headers.get('cf-ray')||'',body:btoa(s)});});})"
				+ ".catch(function(e){send({status:0,error:String(e)});});})();";
	}

	private static synchronized void ensureRouter(CefClient client) {
		if (router == null) {
			router = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig("cakewikiQuery", "cakewikiQueryCancel"),
					new CefMessageRouterHandlerAdapter() {
						@Override
						public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
							return answer(request, callback);
						}
					});
		}
		if (client != null && client != routerClient) {
			client.addMessageRouter(router);
			routerClient = client;
		}
	}

	private static boolean answer(String request, CefQueryCallback callback) {
		JsonObject o;
		try {
			o = JsonParser.parseString(request).getAsJsonObject();
		} catch (RuntimeException e) {
			return false; // not ours
		}
		if (!o.has("id")) {
			return false;
		}
		Pending p = PENDING.get(o.get("id").getAsLong());
		if (p == null) {
			return false;
		}
		callback.success("");
		int status = o.has("status") ? o.get("status").getAsInt() : 0;
		String cf = o.has("cf") ? o.get("cf").getAsString() : "";
		String ray = o.has("ray") ? o.get("ray").getAsString() : "";
		if (status == 200) {
			p.future().complete(Base64.getDecoder().decode(o.get("body").getAsString()));
		} else if (status == 0) {
			p.future().completeExceptionally(new WikiException("network", o.has("error") ? o.get("error").getAsString() : ""));
		} else if (!cf.isEmpty() || status == 403) {
			p.future().completeExceptionally(WikiException.blocked(status, p.host(), ray)); // the pass ran out: ask again
		} else {
			p.future().completeExceptionally(WikiException.http(status, p.host()));
		}
		return true;
	}
}
