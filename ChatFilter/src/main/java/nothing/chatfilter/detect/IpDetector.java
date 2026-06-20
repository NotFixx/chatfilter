package nothing.chatfilter.detect;

import nothing.chatfilter.ConfigManager;
import java.util.regex.Pattern;

public class IpDetector {

    private final Pattern ipPattern;
    private final boolean enabled;

    public IpDetector(ConfigManager config) {
        this.enabled = config.isIpDetectionEnabled();

        if (!enabled) {
            ipPattern = null;
            return;
        }

        StringBuilder regex = new StringBuilder();
        // Standard dotted IP: 192.168.1.1
        regex.append("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");

        if (config.isBlockHexIp()) {
            regex.append("|");
            regex.append("0x[0-9a-fA-F]{1,2}(?:\\.[0-9a-fA-F]{1,2}){3}");
        }

        if (config.isBlockObfuscated()) {
            regex.append("|");
            regex.append("\\b\\d{1,3}\\s*[,;:]\\s*\\d{1,3}\\s*[,;:]\\s*\\d{1,3}\\s*[,;:]\\s*\\d{1,3}\\b");
        }

        this.ipPattern = Pattern.compile(regex.toString());
    }

    public boolean matches(String text) {
        return enabled && ipPattern.matcher(text).find();
    }
}
