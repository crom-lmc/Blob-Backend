# Generate 30 Java tech articles (publish dates spread over the last two years).
# Usage: powershell -ExecutionPolicy Bypass -File scripts/seed-articles.ps1
# Data source: scripts/seed-articles.json
# Articles are written via the admin API, which auto-renders Markdown, computes
# word count / TOC, links tags and recounts categories.

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Base = if ($env:BLOG_BASE) { $env:BLOG_BASE } else { 'http://localhost:8080' }
$RedisCli = if ($env:REDIS_CLI) { $env:REDIS_CLI } else { 'F:\redis-6.2.6\redis-cli.exe' }

function Invoke-Json($Method, $Url, $Body, $Token) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $headers = @{ 'Content-Type' = 'application/json;charset=utf-8' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body $bytes
}

$arts = Get-Content -Raw scripts/seed-articles.json -Encoding UTF8 | ConvertFrom-Json

Write-Host '== login ==' -ForegroundColor Cyan
$Captcha = (Invoke-RestMethod "$Base/api/admin/auth/captcha").data
$Code = (& $RedisCli GET "captcha:$($Captcha.captchaKey)").Trim()
if (-not $Code) { throw 'Cannot read captcha from Redis. Is Redis running?' }
$Login = Invoke-Json 'POST' "$Base/api/admin/auth/login" (@{
        username    = 'admin'
        password    = 'admin123'
        captchaKey  = $Captcha.captchaKey
        captchaCode = $Code
    } | ConvertTo-Json)
if ($Login.code -ne 0) { throw "login failed: $($Login.message)" }
$Token = $Login.data.token
Write-Host "login ok: $($Login.data.user.username)" -ForegroundColor Green

$CatMap = @{}
function Build-CatMap($nodes) {
    foreach ($n in $nodes) {
        if ($n.slug) { $script:CatMap[$n.slug] = $n.id }
        if ($n.children) { Build-CatMap $n.children }
    }
}
Build-CatMap (Invoke-RestMethod "$Base/api/public/categories").data

$ok = 0
foreach ($art in $arts) {
    $catId = $CatMap[$art.categorySlug]
    if (-not $catId) { Write-Host "skip (category missing $($art.categorySlug)): $($art.title)" -ForegroundColor Yellow; continue }
    $payload = @{
        title       = $art.title
        slug        = $art.slug
        summary     = $art.summary
        contentMd   = $art.contentMd
        status      = 'published'
        type        = 'article'
        categoryId  = $catId
        tagNames    = @($art.tags)
        publishedAt = $art.publishedAt
    } | ConvertTo-Json -Depth 8
    try {
        $res = Invoke-Json 'POST' "$Base/api/admin/articles" $payload $Token
        if ($res.code -eq 0) {
            $ok++
            Write-Host ("[{0}/{1}] {2}  {3}" -f $ok, $arts.Count, $art.publishedAt, $art.title)
        }
        else {
            Write-Host "fail: $($art.title) -> $($res.message)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "error: $($art.title) -> $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ''
Write-Host "== done: $ok / $($arts.Count) ==" -ForegroundColor Green
Write-Host 'Visit /api/public/articles for the list, /archives for the timeline'
