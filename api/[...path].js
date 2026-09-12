export default async function handler(req, res) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Headers", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, PUT, POST, OPTIONS");
    res.setHeader("Cache-Control", "no-store");

    if (req.method === "OPTIONS") {
        return res.status(204).end();
    }

    const reqUrl = new URL(req.url, "http://localhost");
    const fullPath = reqUrl.pathname.replace('/api/', '') || '/';

    if (fullPath === "stream") {
        return proxyStream(req, res, reqUrl);
    }

    if (fullPath === "github" || fullPath.startsWith("github/")) {
        return proxyGitHub(req, res, reqUrl, fullPath);
    }

    // ─── PROXY LEGACY ───
    const targetUrl = "https://francope.elementfx.com/" + fullPath + reqUrl.search;

    const proxyHeaders = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept": "*/*",
        "Origin": "https://francope.elementfx.com",
        "Referer": "https://francope.elementfx.com/",
    };

    if (req.headers["range"]) proxyHeaders["Range"] = req.headers["range"];

    try {
        const originResp = await fetch(targetUrl, {
            method: req.method,
            headers: proxyHeaders,
        });

        const contentType = originResp.headers.get("content-type") || "";

        if (contentType.includes("dash+xml") || fullPath.endsWith(".mpd")) {
            let text = await originResp.text();
            text = text.replaceAll("https://francope.elementfx.com", "/api");
            res.setHeader("Content-Type", "application/dash+xml");
            return res.status(originResp.status).send(text);
        }

        res.setHeader("Content-Type", contentType);
        const buffer = await originResp.arrayBuffer();
        return res.status(originResp.status).send(Buffer.from(buffer));

    } catch (e) {
        return res.status(500).send("Proxy error: " + e.message);
    }
}

function getHeader(req, name) {
    const value = req.headers?.[name.toLowerCase()];
    return Array.isArray(value) ? value[0] : value;
}

function parseExtraHeaders(raw) {
    if (!raw) return {};
    try {
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
        return Object.entries(parsed).reduce((headers, [name, value]) => {
            if (/^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/.test(name) && typeof value === "string") {
                headers[name] = value;
            }
            return headers;
        }, {});
    } catch {
        return {};
    }
}

function getStreamProxyUrl(url, userAgent, referer, extraHeaders = {}) {
    const params = new URLSearchParams({ url });
    if (userAgent) params.set("ua", userAgent);
    if (referer) params.set("referer", referer);
    if (Object.keys(extraHeaders).length) params.set("headers", JSON.stringify(extraHeaders));
    return `/api/stream?${params.toString()}`;
}

function rewriteHlsManifest(text, manifestUrl, userAgent, referer, extraHeaders) {
    const rewrite = (value) => {
        if (!value || value.startsWith("data:") || value.startsWith("#")) return value;
        try {
            const absoluteUrl = new URL(value, manifestUrl).toString();
            return getStreamProxyUrl(absoluteUrl, userAgent, referer, extraHeaders);
        } catch {
            return value;
        }
    };

    return text
        .split(/\r?\n/)
        .map(line => {
            if (!line.trim()) return line;
            if (line.startsWith("#")) {
                return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${rewrite(uri)}"`);
            }
            return rewrite(line.trim());
        })
        .join("\n");
}

function rewriteDashManifest(text, manifestUrl, userAgent, referer, extraHeaders) {
    const baseMatch = text.match(/<BaseURL[^>]*>\s*([^<]+?)\s*<\/BaseURL>/i);
    let resolutionBase = manifestUrl;
    if (baseMatch) {
        try { resolutionBase = new URL(baseMatch[1].trim(), manifestUrl).toString(); } catch {}
    }

    const rewrite = (value) => {
        if (!value || value.startsWith("data:") || value.startsWith("#")) return value;
        try {
            const absoluteUrl = new URL(value, resolutionBase).toString();
            return getStreamProxyUrl(absoluteUrl, userAgent, referer, extraHeaders);
        } catch {
            return value;
        }
    };

    return text
        .replace(/\b(media|initialization|sourceURL|xlink:href)="([^"]+)"/gi,
            (_, attr, value) => `${attr}="${rewrite(value)}"`)
        // Los atributos de segmentos ya quedan como URLs absolutas del proxy.
        // No se reemplaza el contenido de BaseURL: hacerlo convertiría una
        // ruta base en una URL de segmento y rompería algunos MPD.
}

async function proxyStream(req, res, reqUrl) {
    const sourceUrl = reqUrl.searchParams.get("url");
    if (!sourceUrl) return res.status(400).send("Falta el parámetro url");

    let target;
    try {
        target = new URL(sourceUrl);
    } catch {
        return res.status(400).send("URL inválida");
    }

    if (!["http:", "https:"].includes(target.protocol)) {
        return res.status(400).send("Solo se permiten URLs HTTP y HTTPS");
    }

    const userAgent = reqUrl.searchParams.get("ua") ||
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    const referer = reqUrl.searchParams.get("referer") || "";
    const extraHeaders = parseExtraHeaders(reqUrl.searchParams.get("headers"));
    const requestHeaders = {
        "User-Agent": userAgent,
        "Accept": "*/*",
        ...extraHeaders,
    };
    if (referer) requestHeaders.Referer = referer;
    if (getHeader(req, "range")) requestHeaders.Range = getHeader(req, "range");

    try {
        const upstream = await fetch(target.toString(), {
            method: req.method === "HEAD" ? "HEAD" : "GET",
            headers: requestHeaders,
        });
        const contentType = upstream.headers.get("content-type") || "";
        const isHlsManifest = contentType.includes("mpegurl") ||
            contentType.includes("vnd.apple.mpegurl") ||
            /\.m3u8(?:$|\?)/i.test(target.pathname + target.search);
        const isDashManifest = contentType.includes("dash+xml") ||
            /\.mpd(?:$|\?)/i.test(target.pathname + target.search);

        res.status(upstream.status);
        if (contentType) res.setHeader("Content-Type", contentType);
        const contentLength = upstream.headers.get("content-length");
        if (contentLength) res.setHeader("Content-Length", contentLength);
        const contentRange = upstream.headers.get("content-range");
        if (contentRange) res.setHeader("Content-Range", contentRange);
        const acceptRanges = upstream.headers.get("accept-ranges");
        if (acceptRanges) res.setHeader("Accept-Ranges", acceptRanges);

        if (req.method === "HEAD") return res.end();

        if (isHlsManifest || isDashManifest) {
            const manifest = await upstream.text();
            res.setHeader("Content-Type", isDashManifest ? "application/dash+xml" : "application/vnd.apple.mpegurl");
            res.removeHeader("Content-Length");
            const manifestBaseUrl = upstream.url || target.toString();
            const rewritten = isDashManifest
                ? rewriteDashManifest(manifest, manifestBaseUrl, userAgent, referer, extraHeaders)
                : rewriteHlsManifest(manifest, manifestBaseUrl, userAgent, referer, extraHeaders);
            return res.send(rewritten);
        }

        if (upstream.body && typeof res.write === "function") {
            const reader = upstream.body.getReader();
            try {
                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    if (value?.length) res.write(Buffer.from(value));
                }
            } finally {
                reader.releaseLock();
            }
            return res.end();
        }

        const buffer = await upstream.arrayBuffer();
        return res.send(Buffer.from(buffer));
    } catch (error) {
        return res.status(502).send(`Error del proxy de reproducción: ${error.message}`);
    }
}

async function readRequestBody(req) {
    if (req.body && typeof req.body === "object") return req.body;
    return await new Promise((resolve, reject) => {
        let raw = "";
        req.on("data", chunk => { raw += chunk; });
        req.on("end", () => {
            if (!raw) return resolve({});
            try { resolve(JSON.parse(raw)); } catch { reject(new Error("JSON inválido")); }
        });
        req.on("error", reject);
    });
}

async function proxyGitHub(req, res, reqUrl, fullPath) {
    const token = process.env.GITHUB_TOKEN;
    if (!token) return res.status(500).send("Falta configurar GITHUB_TOKEN en el servidor");

    const queryPath = reqUrl.searchParams.get("path");
    const githubPath = queryPath || fullPath.replace(/^github\/?/, "");
    if (!githubPath) return res.status(400).send("Falta la ruta de GitHub");

    const githubQuery = new URLSearchParams(reqUrl.search);
    githubQuery.delete("path");
    const queryString = githubQuery.toString();
    const targetUrl = `https://api.github.com/${githubPath}${queryString ? `?${queryString}` : ""}`;
    const headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": `Bearer ${token}`,
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Barto-TV-admin",
    };
    if (req.method !== "GET" && req.method !== "HEAD") {
        headers["Content-Type"] = "application/json";
    }

    try {
        const options = { method: req.method, headers };
        if (req.method !== "GET" && req.method !== "HEAD") {
            options.body = JSON.stringify(await readRequestBody(req));
        }
        const upstream = await fetch(targetUrl, options);
        const body = await upstream.arrayBuffer();
        res.setHeader("Content-Type", upstream.headers.get("content-type") || "application/json");
        return res.status(upstream.status).send(Buffer.from(body));
    } catch (error) {
        return res.status(502).send(`Error del proxy de GitHub: ${error.message}`);
    }
}
