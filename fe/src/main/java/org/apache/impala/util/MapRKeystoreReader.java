package org.apache.impala.util;

import com.mapr.web.security.SslConfig.SslConfigScope;
import com.mapr.web.security.SslConfig;
import com.mapr.web.security.WebSecurityManager;

/**
 * Utility class for ssl default configuration.
 */

public final class MapRKeystoreReader {
  private static final String MAPR_SEC_ENABLED = "mapr_sec_enabled";
  private MapRKeystoreReader() {
  }

  /**
   * Reads client keystore location.
   * @return client keystore location as string
   */

  public static String getClientKeystoreLocation() {
    try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfigScope.SCOPE_CLIENT_ONLY)) {
      return sslConfig.getClientKeystoreLocation();
    }
  }

  /**
   * Reads client password value.
   * @return client password value as string
   */

  public static String getClientKeystorePassword() {
    try (SslConfig sslConfig = WebSecurityManager.getSslConfig(SslConfigScope.SCOPE_CLIENT_ONLY)) {
      return new String(sslConfig.getClientKeystorePassword());

    }
  }

  /**
   * Check if mapr security is enabled on the cluster.
   * Value mapr_sec_enabled is set in $HIVE_BIN/hive script
   * @return true if mapr security is enabled on the cluster
   */

  public static boolean isSecurityEnabled() {
    String mapRSecurityEnabled = System.getProperty(MAPR_SEC_ENABLED);
    if (!isSecurityFlagSet()) {
      return false;
    }
    return "true".equalsIgnoreCase(mapRSecurityEnabled.trim());
  }

  /**
   * Checks if security flag is set and has non empty value.
   * @return true if security flag is set and has non empty value
   */

  public static boolean isSecurityFlagSet() {
    String mapRSecurityEnabled = System.getProperty(MAPR_SEC_ENABLED);
    return mapRSecurityEnabled != null && !mapRSecurityEnabled.isEmpty();
  }
}
