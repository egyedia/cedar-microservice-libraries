package org.metadatacenter.config;

public class ElasticsearchConfig {

  private String cluster;

  private String index;

  private String typeResource;

  private String typePermission;

  private String host;

  private int transportPort;

  private int size;

  private int scrollKeepAlive;

  public String getCluster() {
    return cluster;
  }

  public String getIndex() {
    return index;
  }

  public String getTypeResource() {
    return typeResource;
  }

  public String getTypePermission() {
    return typePermission;
  }

  public String getHost() {
    return host;
  }

  public int getTransportPort() {
    return transportPort;
  }

  public int getSize() {
    return size;
  }

  public int getScrollKeepAlive() {
    return scrollKeepAlive;
  }
}