package com.xiaozhi.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公网IP信息查询服务
 * 提供IP地理位置和运营商信息查询功能
 */
@Component
public class PublicIpInfoProvider {

    private static final Logger logger = LoggerFactory.getLogger(PublicIpInfoProvider.class);

    private static final String[] IP_INFO_SERVICES = {
            "https://www.cip.cc/", // CIP.CC，返回详细信息
            "https://myip.ipip.net/json", // IPIP.net，返回详细信息
    };

    // 运营商关键词
    static final String[] ISP_KEYWORDS = {
            "移动", "联通", "电信", "铁通", "网通", "教育网", "有线通", "长城宽带", "广电网",
            "Mobile", "Unicom", "Telecom", "China Telecom", "China Mobile", "China Unicom",
            "Chinanet", "CMCC", "CHINA UNICOM", "CHINA TELECOM"
    };

    // 云服务商关键词
    static final String[] CLOUD_KEYWORDS = {
            // 国内云服务商
            "阿里云", "腾讯云", "华为云", "百度云", "金山云", "UCloud", "青云", "七牛云",
            "京东云", "天翼云", "移动云", "联通云", "沃云", "浪潮云", "网易云", "美团云",
            "微众银行", "字节跳动", "火山引擎", "快手云", "小米云", "360云", "新浪云", "盛大云",
            "世纪互联", "光环新网", "数梦工场", "云途腾", "云杉网络", "青云QingCloud", "DaoCloud",
            "数据港", "宝德", "云宏", "中国电信云", "中国移动云", "中国联通云", "中科云", "中兴云",

            // 国际云服务商
            "AWS", "Amazon", "Azure", "Microsoft", "Google", "GCP", "Oracle", "IBM",
            "Salesforce", "SAP", "VMware", "Rackspace", "DigitalOcean", "Linode", "Vultr",
            "OVH", "Hetzner", "Scaleway", "Heroku", "CloudFlare", "Akamai", "Fastly",
            "Alibaba Cloud", "Aliyun", "Tencent Cloud", "Huawei Cloud", "Baidu Cloud",
            "ByteDance", "Bytedance", "TikTok", "Douyin", "Volcano Engine",

            // 通用云服务关键词
            "Cloud", "云计算", "云服务", "云平台", "云主机", "云存储", "云数据库", "云网络",
            "IDC", "数据中心", "机房", "服务器集群", "集群", "分布式", "容器云", "Kubernetes",
            "Docker", "虚拟化", "VPS", "ECS", "EC2", "弹性计算", "弹性云服务器", "云服务器",
            "IaaS", "PaaS", "SaaS", "FaaS", "BaaS", "DaaS", "托管云", "混合云", "私有云",
            "公有云", "边缘计算", "CDN", "负载均衡", "高可用", "自动扩展", "弹性伸缩",

            // 云服务商域名关键词
            "aliyun.com", "alibabacloud.com", "cloud.tencent.com", "huaweicloud.com",
            "bce.baidu.com", "ksyun.com", "ucloud.cn", "qingcloud.com", "qiniu.com",
            "jdcloud.com", "ctyun.cn", "amazonaws.com", "azure.com", "microsoft.com",
            "cloud.google.com", "oracle.com", "ibm.com", "salesforce.com", "sap.com",
            "vmware.com", "rackspace.com", "digitalocean.com", "linode.com", "vultr.com",
            "ovh.com", "hetzner.com", "scaleway.com", "heroku.com", "cloudflare.com",
            "akamai.com", "fastly.com", "volcengine.com", "bytecdn.com", "byted.org",
            "bytedance.com"
    };

    // 私有IP地址段
    static final String[] PRIVATE_IP_PATTERNS = {
            "^10\\..*", // 10.0.0.0 - 10.255.255.255
            "^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*", // 172.16.0.0 - 172.31.255.255
            "^192\\.168\\..*" // 192.168.0.0 - 192.168.255.255
    };

    /**
     * IP信息类
     */
    public static class IPInfo {
        private String ip;
        private String location;
        private String isp;
        private boolean isCloudProvider;
        private boolean isPrivateIp;

        public IPInfo(String ip, String location, String isp) {
            this.ip = ip;
            this.location = location != null ? location : "";
            this.isp = isp != null ? isp : "";
            this.isCloudProvider = checkIsCloudProvider();
            this.isPrivateIp = checkIsPrivateIp();
        }

        public String getIp() {
            return ip;
        }

        public String getLocation() {
            return location;
        }

        public String getIsp() {
            return isp;
        }

        public boolean isCloudProvider() {
            return isCloudProvider;
        }

        public boolean isPrivateIp() {
            return isPrivateIp;
        }

        /**
         * 判断是否为服务器环境
         * 1. 如果是云服务商IP段，则认为是服务器环境
         * 2. 如果IP信息中包含云服务商关键词，则认为是服务器环境
         * 3. 如果不是私有IP，且不是常见运营商IP，则可能是服务器环境
         */
        public boolean isServerEnvironment() {
            // 如果是云服务商IP段或者IP信息中包含云服务商关键词，则认为是服务器环境
            if (isCloudProvider) {
                return true;
            }

            // 如果是私有IP，则不是服务器环境
            if (isPrivateIp) {
                return false;
            }

            // 检查是否为运营商IP
            boolean isIsp = false;
            for (String keyword : ISP_KEYWORDS) {
                if (isp.contains(keyword)) {
                    isIsp = true;
                    break;
                }
            }

            // 如果不是运营商IP，则可能是服务器环境
            return !isIsp;
        }

        /**
         * 检查是否为云服务商IP（仅通过关键词判断）
         */
        private boolean checkIsCloudProvider() {
            // 检查IP信息中是否包含云服务商关键词
            for (String keyword : CLOUD_KEYWORDS) {
                if (location.contains(keyword) || isp.contains(keyword)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * 检查是否为私有IP
         */
        private boolean checkIsPrivateIp() {
            for (String pattern : PRIVATE_IP_PATTERNS) {
                if (ip.matches(pattern)) {
                    return true;
                }
            }

            return ip.startsWith("127.") || ip.equals("0.0.0.0") || ip.equals("localhost");
        }
    }

    /**
     * 根据指定IP地址获取地理位置信息
     */
    public static IPInfo getIPInfoByAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty() || "127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return null;
        }

        // 对于私有IP地址，不进行地理位置查询
        if (IpAddressValidator.isPrivateIp(ipAddress)) {
            return new IPInfo(ipAddress, null, "内网");
        }

        // 首先尝试使用现有的IP_INFO_SERVICES（优先使用已有服务）
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            String queryUrl = IP_INFO_SERVICES[0];
            // cip.cc 支持直接在URL后添加IP参数
            queryUrl = queryUrl + ipAddress;

            URL url = new URL(queryUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String content = response.toString();
                IPInfo ipInfo = parseIPInfo(queryUrl, content);
                if (ipInfo != null) {
                    // 强制设置IP地址为指定的IP（因为服务可能返回的是其他IP）
                    return new IPInfo(ipAddress, ipInfo.getLocation(), ipInfo.getIsp());
                }
            }
        } catch (Exception e) {
            logger.debug("查询IP {} 失败: {}", ipAddress, e.getMessage());
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                // ignore
            }
        }

        // 如果现有服务都无法查询，返回基本的IP信息
        return new IPInfo(ipAddress, "未知位置", "未知运营商");
    }

    /**
     * 获取IP信息（服务器公网IP）
     */
    public static IPInfo getIPInfo() {
        for (String service : IP_INFO_SERVICES) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            long startTime = System.currentTimeMillis();

            try {
                URL url = new URL(service);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000); // 3秒连接超时
                connection.setReadTimeout(3000); // 3秒读取超时
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

                // 开始连接
                connection.connect();

                // 检查是否超时
                if (System.currentTimeMillis() - startTime > 3000) {
                    continue;
                }

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;

                    // 设置最大读取时间
                    long maxReadTime = startTime + 3000;

                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");

                        // 检查是否超过最大读取时间
                        if (System.currentTimeMillis() > maxReadTime) {
                            break;
                        }
                    }

                    // 如果超时了但已经读取了部分数据，继续处理
                    if (System.currentTimeMillis() <= maxReadTime || response.length() > 0) {
                        String content = response.toString();

                        // 解析IP信息
                        IPInfo ipInfo = parseIPInfo(service, content);
                        if (ipInfo != null) {
                            return ipInfo;
                        }
                    }
                } else {
                    logger.warn("IP信息服务返回非200状态码: {} - {}", service, connection.getResponseCode());
                }
            } catch (java.net.SocketTimeoutException e) {
                logger.warn("获取IP信息超时，切换到下一个服务: {} - {}", service, e.getMessage());
            } catch (Exception e) {
                logger.warn("获取IP信息失败: {} - {}", service, e.getMessage());
            } finally {
                // 关闭资源
                try {
                    if (reader != null) {
                        reader.close();
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                } catch (Exception e) {
                    logger.warn("关闭资源失败: {}", e.getMessage());
                }
            }

        }

        return null;
    }

    /**
     * 从不同服务的响应中解析IP信息
     */
    static IPInfo parseIPInfo(String service, String content) {
        try {
            if (service.contains("cip.cc")) {
                // 提取IP
                Pattern patternIp = Pattern.compile("IP\\s*:\\s*([\\d.]+)");
                Matcher matcherIp = patternIp.matcher(content);

                if (matcherIp.find()) {
                    String ip = matcherIp.group(1);

                    // 提取地址和运营商信息 - 改进解析逻辑
                    String location = "";
                    String isp = "";

                    // 解析地址信息 - 查找格式为 "地址 : xxx" 的行
                    Pattern patternAddr = Pattern.compile("地址\\s*:\\s*([^\n]+)");
                    Matcher matcherAddr = patternAddr.matcher(content);
                    if (matcherAddr.find()) {
                        location = matcherAddr.group(1).trim();
                        // 只保留基本地理位置信息，通常是"国家 省份 城市"格式
                        if (location.contains(" ")) {
                            String[] parts = location.split("\\s+");
                            // 取前三个部分作为地址信息
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < Math.min(parts.length, 3); i++) {
                                if (!parts[i].isEmpty()) {
                                    if (sb.length() > 0) {
                                        sb.append(" ");
                                    }
                                    sb.append(parts[i]);
                                }
                            }
                            location = sb.toString();
                        }
                    }

                    // 解析运营商信息 - 查找格式为 "运营商 : xxx" 的行
                    Pattern patternIsp = Pattern.compile("运营商\\s*:\\s*([^\n]+)");
                    Matcher matcherIsp = patternIsp.matcher(content);
                    if (matcherIsp.find()) {
                        isp = matcherIsp.group(1).trim();
                        // 只保留运营商名称，去除可能的额外信息
                        for (String keyword : ISP_KEYWORDS) {
                            if (isp.contains(keyword)) {
                                isp = keyword;
                                break;
                            }
                        }

                        // 如果没有匹配到关键词，则取第一个词作为运营商名称
                        if (isp.contains(" ")) {
                            isp = isp.split("\\s+")[0];
                        }
                    }

                    // 如果无法提取运营商，尝试从地址中提取
                    if (isp.isEmpty()) {
                        String originalLocation = matcherAddr.group(1).trim();
                        for (String keyword : ISP_KEYWORDS) {
                            if (originalLocation.contains(keyword)) {
                                isp = keyword;
                                break;
                            }
                        }
                    }

                    return new IPInfo(ip, location, isp);
                }
            } else if (service.contains("ipip.net")) {
                // IPIP.net
                // {"ret":"ok","data":{"ip":"139.226.72.136","location":["中国","上海","上海","","联通"]}}
                Pattern patternIp = Pattern.compile("\"ip\":\"([\\d.]+)\"");
                Pattern patternCountry = Pattern.compile("\\[\"([^\"]*?)\""); // 匹配location数组中的第一个元素(国家)
                Pattern patternProvince = Pattern.compile("\\[\"[^\"]*?\",\"([^\"]*?)\""); // 匹配location数组中的第二个元素(省份)
                Pattern patternCity = Pattern.compile("\\[\"[^\"]*?\",\"[^\"]*?\",\"([^\"]*?)\""); // 匹配location数组中的第三个元素(城市)
                Pattern patternIspLocal = Pattern
                        .compile("\\[\"[^\"]*?\",\"[^\"]*?\",\"[^\"]*?\",\"[^\"]*?\",\"([^\"]*?)\""); // 匹配location数组中的第五个元素(运营商)
                Matcher matcherIp = patternIp.matcher(content);
                Matcher matcherCountry = patternCountry.matcher(content);
                Matcher matcherProvince = patternProvince.matcher(content);
                Matcher matcherCity = patternCity.matcher(content);
                Matcher matcherIsp = patternIspLocal.matcher(content);

                if (matcherIp.find()) {
                    String ip = matcherIp.group(1);
                    String country = matcherCountry.find() ? matcherCountry.group(1) : "";
                    String province = matcherProvince.find() ? matcherProvince.group(1) : "";
                    String city = matcherCity.find() ? matcherCity.group(1) : "";
                    String ispVal = matcherIsp.find() ? matcherIsp.group(1) : "";

                    String locationStr = country + " " + province + " " + city;
                    locationStr = locationStr.trim().replaceAll("\\s+", " ");
                    return new IPInfo(ip, locationStr, ispVal);
                }
            }
        } catch (Exception e) {
            logger.warn("解析IP信息失败: {}", e.getMessage());
            // 解析异常，返回null
        }

        return null;
    }
}
