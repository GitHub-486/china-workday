# CFCA 根证书管理（gov.cn 抓取信任库）

**背景**：`china-workday` 抓取 gov.cn 节假日通知需信任 **CFCA EV ROOT** 根证书。
JDK/JRE 默认 `cacerts` 不含该证书，若不导入会报 PKIX 证书路径错误：

```
PKIX path building failed: unable to find valid certification path to requested target
```

本机 JDK 路径：`D:\Java_Soft\Java\jdk-17`（应用必须以 JDK17 启动，JDK8 未导入会复现此错误）。
临时证书文件：`cfca-ev-root.der`。

---

## 一、导入证书到信任库（cacerts）

### PowerShell

```powershell
& "D:\Java_Soft\Java\jdk-17\bin\keytool.exe" -importcert -noprompt `
  -alias cfca-ev-root `
  -file "D:\Java_workspace\idea_workspace\Gitee\script_4\china-workday\cfca-ev-root.der" `
  -keystore "D:\Java_Soft\Java\jdk-17\lib\security\cacerts" `
  -storepass changeit
```

### CMD（cmd.exe）

```cmd
keytool -importcert -noprompt -alias cfca-ev-root ^
  -file "D:\Java_workspace\idea_workspace\Gitee\script_4\china-workday\cfca-ev-root.der" ^
  -keystore "D:\Java_Soft\Java\jdk-17\lib\security\cacerts" ^
  -storepass changeit
```

> 提示：
> - JDK17 也支持 `-cacerts` 快捷选项（PATH 里需是 jdk17 的 keytool）。
> - 若已存在同名别名，会提示确认覆盖；重复导入可先删除再导入。

---

## 二、从信任库删除该证书（回滚）

### PowerShell

```powershell
& "D:\Java_Soft\Java\jdk-17\bin\keytool.exe" -delete `
  -alias cfca-ev-root `
  -keystore "D:\Java_Soft\Java\jdk-17\lib\security\cacerts" `
  -storepass changeit
```

### CMD（cmd.exe）

```cmd
keytool -delete -alias cfca-ev-root ^
  -keystore "D:\Java_Soft\Java\jdk-17\lib\security\cacerts" ^
  -storepass changeit
```

---

## 三、验证是否已导入

### PowerShell

```powershell
& "D:\Java_Soft\Java\jdk-17\bin\keytool.exe" -list `
  -keystore "D:\Java_Soft\Java\jdk-17\lib\security\cacerts" `
  -storepass changeit | Select-String "cfca-ev-root"
```

### CMD（cmd.exe）

```cmd
keytool -list -keystore "D:\Java_Soft\Java\jdk-17\lib\security\cacerts" -storepass changeit | findstr cfca-ev-root
```

正常输出会出现：

```
cfca-ev-root, 2026年10月22日, trustedCertEntry,
```

---

## 四、删除临时证书文件本身（不是回滚，只是清理文件）

删除 `cfca-ev-root.der` 只移除磁盘文件，**不影响**已导入 cacerts 的证书。
只有当你不再需要在其它 JDK/容器里复用此证书时才删除。

### PowerShell

```powershell
Remove-Item "D:\Java_workspace\idea_workspace\Gitee\script_4\china-workday\cfca-ev-root.der"
```

### CMD（cmd.exe）

```cmd
del "D:\Java_workspace\idea_workspace\Gitee\script_4\china-workday\cfca-ev-root.der"
```

---

## 五、TLS 连通性验证（确认信任库生效）

用 JDK17 实测 gov.cn（返回 200 即正常，非 PKIX 即成功）：

### 命令行（一次性小脚本）

```powershell
$content = @'
import java.net.http.*;
import java.net.URI;
public class TlsOk {
  public static void main(String[] a) throws Exception {
    HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    HttpRequest r = HttpRequest.newBuilder(URI.create("https://sousuo.www.gov.cn/search-gov/data?t=zhengcelibrary&q=%E8%8A%82%E5%81%87%E6%97%A5&p=1&n=1&type=gwyzcwjk"))
      .timeout(java.time.Duration.ofSeconds(20)).header("User-Agent","Mozilla/5.0 Chrome/120").GET().build();
    HttpResponse<String> resp = c.send(r, HttpResponse.BodyHandlers.ofString());
    System.out.println("HTTP "+resp.statusCode()+" len="+resp.body().length());
  }
}
'@
[System.IO.File]::WriteAllText("$PWD\target\TlsOk.java", $content, (New-Object System.Text.UTF8Encoding $false))
& "D:\Java_Soft\Java\jdk-17\bin\java.exe" "$PWD\target\TlsOk.java"
```

同样适用：`GET https://www.gov.cn/zhengce/zhengceku/` 等 gov.cn 域名。

---

## 六、常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动仍报 PKIX | 应用用了 JDK8（未导入） | 用 JDK17 运行，确认 JAVA_HOME / IDE 运行配置 |
| 导入后 keytool -list 无该别名 | cacerts 被重置 / JDK 被重装 | 重新导出并导入（本文件第二节） |
| `cfca-ev-root.der` 为 0 字节 | 文件被截断/覆盖 | 重新从 gov.cn TLS 链导出（见下文） |

### 重新导出 cfca-ev-root.der（文件丢失或损坏时）

```powershell
$tcp = New-Object System.Net.Sockets.TcpClient
$tcp.Connect("www.gov.cn", 443)
$ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, { param($s,$c,$ch,$e) $true })
$ssl.AuthenticateAsClient("www.gov.cn")
$chain = New-Object System.Security.Cryptography.X509Certificates.X509Chain
$chain.Build($ssl.RemoteCertificate) | Out-Null
$root = $chain.ChainElements[$chain.ChainElements.Count - 1].Certificate
[System.IO.File]::WriteAllBytes("$PWD\cfca-ev-root.der", $root.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
$ssl.Close(); $tcp.Close()
Get-Item "$PWD\cfca-ev-root.der" | Select-Object Name,Length
```

导出后应显示：`Subject: CN=CFCA EV ROOT, O=China Financial Certification Authority, C=CN`，大小约 1425 字节。
