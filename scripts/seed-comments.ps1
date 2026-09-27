# Seed comments for published articles.
# - 5..20 comments per article, some with one reply
# - statuses randomly assigned: approved / pending / spam
# Generates SQL directly (bypassing rate-limit + review flow) and fixes comment_count.
# Idempotent: clears existing t_comment first.
# Usage: powershell -ExecutionPolicy Bypass -File scripts/seed-comments.ps1

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$BaseDir = $PSScriptRoot
$Mysql = if ($env:MYSQL) { $env:MYSQL } else { 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe' }
$User = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { 'root' }
$Pass = if ($env:MYSQL_PASS) { $env:MYSQL_PASS } else { '123456' }
$Db   = if ($env:MYSQL_DB)   { $env:MYSQL_DB }   else { 'blog' }

function Sql-Escape($s) {
    if ($null -eq $s) { return 'NULL' }
    return "'" + ($s.ToString() -replace "'", "''") + "'"
}

# ---- pools ----
$data = Get-Content -Raw (Join-Path $BaseDir 'seed-comments.json') -Encoding UTF8 | ConvertFrom-Json

# ---- article list (id, slug, published_at) ----
$tmpList = Join-Path $BaseDir 'seed-comments-list.tmp'
& cmd /c "`"$Mysql`" -u$User -p$Pass --show-warnings=0 -N -B -e `"SELECT id, slug, published_at FROM blog.t_article WHERE status='published' ORDER BY id`" > `"$tmpList`" 2>nul"
$rows = Get-Content $tmpList -ErrorAction SilentlyContinue
Remove-Item $tmpList -Force -ErrorAction SilentlyContinue
$articles = @()
foreach ($line in ($rows | Where-Object { $_.Trim() -ne '' })) {
    $f = $line -split "`t"
    if ($f.Count -ge 3) {
        $articles += [PSCustomObject]@{ id = [long]$f[0]; slug = $f[1]; pub = $f[2] }
    }
}
Write-Host ("== found {0} published articles ==" -f $articles.Count) -ForegroundColor Cyan

# ---- author material ----
$names = @($data.meta.names)
$adminName = $data.meta.adminName
$emails = @('a1@qq.com','dev@example.com','coder@163.com','lee@gmail.com','wang@foxmail.com','zhao@outlook.com','sun@sina.com','zhou@126.com')
$ips = @('116.22.33.4','101.88.12.9','223.104.5.18','120.244.18.77','36.110.22.51','58.246.33.9','111.30.131.5','39.144.8.22')
$uas = @(
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36',
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1',
    'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36'
)

$now = Get-Date
$sql = "SET NAMES utf8mb4;`r`nDELETE FROM t_comment;`r`nALTER TABLE t_comment AUTO_INCREMENT = 1;`r`nUPDATE t_article SET comment_count = 0;`r`n"

$id = 0
$topIdByArticle = @{}
$totalTop = 0
$totalReply = 0

foreach ($art in $articles) {
    $pool = if ($data.PSObject.Properties[$art.slug]) { $data.$($art.slug) } else { $data.welcome }
    $cmts = @($pool.comments); $reps = @($pool.replies); $spm = @($pool.spam)
    $maxN = [math]::Min(20, $cmts.Count)
    if ($maxN -lt 5) { $maxN = 5 }
    $n = Get-Random -Minimum 5 -Maximum ($maxN + 1)
    $shuffled = $cmts | Sort-Object { Get-Random } | Select-Object -First $n

    try { $pubDate = [datetime]::Parse($art.pub) } catch { $pubDate = $now.AddDays(-30) }
    if ($pubDate -gt $now) { $pubDate = $now.AddDays(-1) }

    foreach ($c in $shuffled) {
        $roll = Get-Random -Minimum 0 -Maximum 100
        if ($roll -lt 12) {
            $status = 'spam'; $content = ($spm | Get-Random)
        } elseif ($roll -lt 42) {
            $status = 'pending'; $content = $c
        } else {
            $status = 'approved'; $content = $c
        }
        $author = ($names | Get-Random)
        $email  = ($emails | Get-Random)
        $ip     = ($ips | Get-Random)
        $ua     = ($uas | Get-Random)

        $spanMin = [int]($now - $pubDate).TotalMinutes
        if ($spanMin -lt 1) { $spanMin = 1 }
        $created = $pubDate.AddMinutes((Get-Random -Minimum 0 -Maximum ($spanMin + 1)))

        $id++
        $topId = $id
        $totalTop++
        $sql += ("INSERT INTO t_comment (id, article_id, parent_id, author_name, author_email, author_site, author_avatar, content, status, user_agent, ip, is_admin, created_at) VALUES ({0}, {1}, 0, {2}, {3}, NULL, NULL, {4}, '{5}', {6}, {7}, 0, '{8}');`r`n" `
            -f $topId, $art.id, (Sql-Escape $author), (Sql-Escape $email), (Sql-Escape $content), $status, (Sql-Escape $ua), (Sql-Escape $ip), $created.ToString('yyyy-MM-dd HH:mm:ss'))

        # maybe a reply
        if ((Get-Random -Minimum 0 -Maximum 100) -lt 38) {
            $repContent = ($reps | Get-Random)
            $rStatus = if ((Get-Random -Minimum 0 -Maximum 100) -lt 15) { 'pending' } else { 'approved' }
            $isAdmin = 0; $rAuthor = ($names | Get-Random); $rEmail = ($emails | Get-Random)
            if ((Get-Random -Minimum 0 -Maximum 100) -lt 30) {
                $isAdmin = 1; $rAuthor = $adminName; $rEmail = 'admin@blog.com'; $rStatus = 'approved'
            }
            $rCreated = $created.AddMinutes((Get-Random -Minimum 1 -Maximum 600))
            if ($rCreated -gt $now) { $rCreated = $now }
            $id++
            $totalReply++
            $sql += ("INSERT INTO t_comment (id, article_id, parent_id, author_name, author_email, author_site, author_avatar, content, status, user_agent, ip, is_admin, created_at) VALUES ({0}, {1}, {2}, {3}, {4}, NULL, NULL, {5}, '{6}', {7}, {8}, {9}, '{10}');`r`n" `
                -f $id, $art.id, $topId, (Sql-Escape $rAuthor), (Sql-Escape $rEmail), (Sql-Escape $repContent), $rStatus, (Sql-Escape $ua), (Sql-Escape $ip), $isAdmin, $rCreated.ToString('yyyy-MM-dd HH:mm:ss'))
        }
    }
}

# recompute comment_count (only approved, including replies) for all articles
$sql += @"
UPDATE t_article a
LEFT JOIN (SELECT article_id, COUNT(*) cnt FROM t_comment WHERE status='approved' GROUP BY article_id) c
  ON c.article_id = a.id
SET a.comment_count = COALESCE(c.cnt, 0);
SELECT status, COUNT(*) AS cnt FROM t_comment GROUP BY status;
SELECT COUNT(DISTINCT article_id) AS articles_with_comments, COUNT(*) AS total_comments FROM t_comment;
"@

$sqlFile = Join-Path $BaseDir 'seed-comments.tmp.sql'
[System.IO.File]::WriteAllText($sqlFile, $sql, [System.Text.UTF8Encoding]::new($false))

Write-Host ("== generating {0} top-level + {1} replies ==" -f $totalTop, $totalReply) -ForegroundColor Cyan
& cmd /c "`"$Mysql`" -u$User -p$Pass --show-warnings=0 --default-character-set=utf8mb4 $Db < `"$sqlFile`" 2>nul"
Remove-Item $sqlFile -Force

Write-Host '== done ==' -ForegroundColor Green
