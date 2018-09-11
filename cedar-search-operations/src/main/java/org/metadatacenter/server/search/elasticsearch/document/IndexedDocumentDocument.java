package org.metadatacenter.server.search.elasticsearch.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.folderserver.FolderServerNodeInfo;
import org.metadatacenter.model.folderserverextract.FolderServerNodeExtract;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedDocumentDocument {

  protected String cid;

  protected String summaryText;

  protected FolderServerNodeInfo info;

  public IndexedDocumentDocument() {
  }

  public IndexedDocumentDocument(String cid) {
    this.cid = cid;
  }

  public String getCid() {
    return cid;
  }

  public String getSummaryText() {
    return summaryText;
  }

  public void setSummaryText(String summaryText) {
    this.summaryText = summaryText;
  }

  public FolderServerNodeInfo getInfo() {
    return info;
  }

  @JsonIgnore
  public FolderServerNodeExtract getInfoExtract() {
    return FolderServerNodeExtract.fromNodeInfo(info);
  }

  public void setInfo(FolderServerNodeInfo info) {
    this.info = info;
  }

}
