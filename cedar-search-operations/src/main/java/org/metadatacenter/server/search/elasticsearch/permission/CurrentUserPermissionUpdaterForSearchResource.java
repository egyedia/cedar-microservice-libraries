package org.metadatacenter.server.search.elasticsearch.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.ResourceUri;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CurrentUserPermissions;
import org.metadatacenter.server.security.model.user.CedarUser;

public class CurrentUserPermissionUpdaterForSearchResource extends AbstractCurrentUserPermissionUpdaterForSearch {

  private CurrentUserPermissionUpdaterForSearchResource(IndexedDocumentDocument indexedDocument, CedarUser cedarUser,
                                                        CedarConfig cedarConfig) {
    super(indexedDocument, cedarUser, cedarConfig);
  }

  public static CurrentUserPermissionUpdater get(IndexedDocumentDocument indexedDocument, CedarUser cedarUser,
                                                 CedarConfig cedarConfig) {
    return new CurrentUserPermissionUpdaterForSearchResource(indexedDocument, cedarUser, cedarConfig);
  }

  @Override
  public void update(CurrentUserPermissions currentUserPermissions) {
    if (userCanWrite()) {
      currentUserPermissions.setCanWrite(true);
      currentUserPermissions.setCanDelete(true);
      currentUserPermissions.setCanRead(true);
      currentUserPermissions.setCanShare(true);
    } else if (userCanRead()) {
      currentUserPermissions.setCanRead(true);
    }

    if (userCanChangeOwnerOfFolder()) {
      currentUserPermissions.setCanChangeOwner(true);
    }

    currentUserPermissions.setCanCopy(true);

    if (indexedDocument.getInfo().getType() == CedarNodeType.TEMPLATE) {
      currentUserPermissions.setCanPopulate(true);
    }


    if (userCanPerformVersioning()) {
      if (resourceCanBePublished()) {
        currentUserPermissions.setCanPublish(true);
      }
      if (resourceCanBeDrafted()) {
        currentUserPermissions.setCanCreateDraft(true);
      }
    }
    if (indexedDocument.getInfo().getType() == CedarNodeType.INSTANCE) {
      if (isSubmittable()) {
        currentUserPermissions.setCanSubmit(true);
      }
    }
  }

  private boolean isSubmittable() {
    ResourceUri basedOnTemplate = indexedDocument.getInfo().getIsBasedOn();
    if (basedOnTemplate != null) {
      String basedOnTemplateId = basedOnTemplate.getValue();
      return cedarConfig.getSubmissionConfig().getSubmittableTemplateIds() != null &&
          cedarConfig.getSubmissionConfig().getSubmittableTemplateIds().contains(basedOnTemplateId);
    }
    return false;
  }
}