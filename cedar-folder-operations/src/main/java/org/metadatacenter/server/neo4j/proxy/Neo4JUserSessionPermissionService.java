package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.model.folderserver.FolderServerGroup;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.folderserver.FolderServerUser;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.user.CedarGroupExtract;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Neo4JUserSessionPermissionService extends AbstractNeo4JUserSession implements PermissionServiceSession {

  public Neo4JUserSessionPermissionService(Neo4JProxies proxies, CedarUser cu, String userIdPrefix, String
      groupIdPrefix) {
    super(proxies, cu, userIdPrefix, groupIdPrefix);
  }

  public static PermissionServiceSession get(Neo4JProxies proxies, CedarUser cedarUser) {
    return new Neo4JUserSessionPermissionService(proxies, cedarUser, proxies.getUserIdPrefix(),
        proxies.getGroupIdPrefix());
  }

  @Override
  public CedarNodePermissions getNodePermissions(String nodeURL, boolean nodeIsFolder) {
    FolderServerNode node;
    if (nodeIsFolder) {
      node = proxies.folder().findFolderById(nodeURL);
    } else {
      node = proxies.resource().findResourceById(nodeURL);
    }
    if (node != null) {
      FolderServerUser owner = getNodeOwner(nodeURL);
      List<FolderServerUser> readUsers = getUsersWithPermission(nodeURL, NodePermission.READ);
      List<FolderServerUser> writeUsers = getUsersWithPermission(nodeURL, NodePermission.WRITE);
      List<FolderServerGroup> readGroups = getGroupsWithPermission(nodeURL, NodePermission.READ);
      List<FolderServerGroup> writeGroups = getGroupsWithPermission(nodeURL, NodePermission.WRITE);
      return buildPermissions(owner, readUsers, writeUsers, readGroups, writeGroups);
    } else {
      return null;
    }
  }

  private FolderServerUser getNodeOwner(String nodeURL) {
    return proxies.node().getNodeOwner(nodeURL);
  }

  private List<FolderServerUser> getUsersWithPermission(String nodeURL, NodePermission permission) {
    return proxies.permission().getUsersWithPermissionOnNode(nodeURL, permission);
  }

  private List<FolderServerGroup> getGroupsWithPermission(String nodeURL, NodePermission permission) {
    return proxies.permission().getGroupsWithPermissionOnNode(nodeURL, permission);
  }

  @Override
  public BackendCallResult updateNodePermissions(String nodeURL, CedarNodePermissionsRequest request,
                                                 boolean nodeIsFolder) {

    PermissionRequestValidator prv = new PermissionRequestValidator(this, proxies, nodeURL, request, nodeIsFolder);
    BackendCallResult bcr = prv.getCallResult();
    if (bcr.isError()) {
      return bcr;
    } else {
      CedarNodePermissions currentPermissions = getNodePermissions(nodeURL, nodeIsFolder);
      CedarNodePermissions newPermissions = prv.getPermissions();

      String oldOwnerId = currentPermissions.getOwner().getId();
      String newOwnerId = newPermissions.getOwner().getId();
      if (oldOwnerId != null && !oldOwnerId.equals(newOwnerId)) {
        Neo4JUserSessionGroupOperations.updateNodeOwner(proxies.node(), nodeURL, newOwnerId, nodeIsFolder);
      }

      Set<NodePermissionUserPermissionPair> oldUserPermissions = new HashSet<>();
      for (CedarNodeUserPermission up : currentPermissions.getUserPermissions()) {
        oldUserPermissions.add(up.getAsUserIdPermissionPair());
      }
      Set<NodePermissionUserPermissionPair> newUserPermissions = new HashSet<>();
      for (CedarNodeUserPermission up : newPermissions.getUserPermissions()) {
        newUserPermissions.add(up.getAsUserIdPermissionPair());
      }

      Set<NodePermissionUserPermissionPair> toRemoveUserPermissions = new HashSet<>();
      toRemoveUserPermissions.addAll(oldUserPermissions);
      toRemoveUserPermissions.removeAll(newUserPermissions);
      if (!toRemoveUserPermissions.isEmpty()) {
        Neo4JUserSessionGroupOperations.removeUserPermissions(proxies.permission(), nodeURL, toRemoveUserPermissions,
            nodeIsFolder);
      }

      Set<NodePermissionUserPermissionPair> toAddUserPermissions = new HashSet<>();
      toAddUserPermissions.addAll(newUserPermissions);
      toAddUserPermissions.removeAll(oldUserPermissions);
      if (!toAddUserPermissions.isEmpty()) {
        Neo4JUserSessionGroupOperations.addUserPermissions(proxies.permission(), nodeURL, toAddUserPermissions,
            nodeIsFolder);
      }

      Set<NodePermissionGroupPermissionPair> oldGroupPermissions = new HashSet<>();
      for (CedarNodeGroupPermission gp : currentPermissions.getGroupPermissions()) {
        oldGroupPermissions.add(gp.getAsGroupIdPermissionPair());
      }
      Set<NodePermissionGroupPermissionPair> newGroupPermissions = new HashSet<>();
      for (CedarNodeGroupPermission gp : newPermissions.getGroupPermissions()) {
        newGroupPermissions.add(gp.getAsGroupIdPermissionPair());
      }

      Set<NodePermissionGroupPermissionPair> toRemoveGroupPermissions = new HashSet<>();
      toRemoveGroupPermissions.addAll(oldGroupPermissions);
      toRemoveGroupPermissions.removeAll(newGroupPermissions);
      if (!toRemoveGroupPermissions.isEmpty()) {
        Neo4JUserSessionGroupOperations.removeGroupPermissions(proxies.permission(), nodeURL, toRemoveGroupPermissions,
            nodeIsFolder);
      }

      Set<NodePermissionGroupPermissionPair> toAddGroupPermissions = new HashSet<>();
      toAddGroupPermissions.addAll(newGroupPermissions);
      toAddGroupPermissions.removeAll(oldGroupPermissions);
      if (!toAddGroupPermissions.isEmpty()) {
        Neo4JUserSessionGroupOperations.addGroupPermissions(proxies.permission(), nodeURL, toAddGroupPermissions,
            nodeIsFolder);
      }
      return new BackendCallResult();
    }
  }

  @Override
  public boolean userIsOwnerOfFolder(String folderURL) {
    FolderServerUser owner = getNodeOwner(folderURL);
    return owner != null && owner.getId().equals(getUserId());
  }

  @Override
  public boolean userHasReadAccessToFolder(String folderURL) {
    return proxies.permission().userHasReadAccessToFolder(getUserId(), folderURL) || proxies.permission()
        .userHasWriteAccessToFolder(cu.getId(), folderURL);
  }

  @Override
  public boolean userHasWriteAccessToFolder(String folderURL) {
    return proxies.permission().userHasWriteAccessToFolder(getUserId(), folderURL);
  }

  @Override
  public boolean userIsOwnerOfResource(String resourceURL) {
    FolderServerUser owner = getNodeOwner(resourceURL);
    return owner != null && owner.getId().equals(getUserId());
  }

  @Override
  public boolean userHasReadAccessToResource(String resourceURL) {
    return proxies.permission().userHasReadAccessToResource(getUserId(), resourceURL) || proxies.permission()
        .userHasWriteAccessToFolder(cu.getId(), resourceURL);
  }

  @Override
  public boolean userHasWriteAccessToResource(String resourceURL) {
    return proxies.permission().userHasWriteAccessToResource(getUserId(), resourceURL);
  }

  @Override
  public boolean userIsOwnerOfNode(FolderServerNode node) {
    FolderServerUser nodeOwner = getNodeOwner(node.getId());
    return nodeOwner != null && nodeOwner.getId().equals(getUserId());
  }


  private CedarNodePermissions buildPermissions(FolderServerUser owner, List<FolderServerUser> readUsers,
                                                List<FolderServerUser> writeUsers, List<FolderServerGroup>
                                                    readGroups, List<FolderServerGroup> writeGroups) {
    CedarNodePermissions permissions = new CedarNodePermissions();
    CedarUserExtract o = owner.buildExtract();
    permissions.setOwner(o);
    if (readUsers != null) {
      for (FolderServerUser user : readUsers) {
        CedarUserExtract u = user.buildExtract();
        CedarNodeUserPermission up = new CedarNodeUserPermission(u, NodePermission.READ);
        permissions.addUserPermissions(up);
      }
    }
    if (writeUsers != null) {
      for (FolderServerUser user : writeUsers) {
        CedarUserExtract u = user.buildExtract();
        CedarNodeUserPermission up = new CedarNodeUserPermission(u, NodePermission.WRITE);
        permissions.addUserPermissions(up);
      }
    }
    if (readGroups != null) {
      for (FolderServerGroup group : readGroups) {
        CedarGroupExtract g = group.buildExtract();
        CedarNodeGroupPermission gp = new CedarNodeGroupPermission(g, NodePermission.READ);
        permissions.addGroupPermissions(gp);
      }
    }
    if (writeGroups != null) {
      for (FolderServerGroup group : writeGroups) {
        CedarGroupExtract g = group.buildExtract();
        CedarNodeGroupPermission gp = new CedarNodeGroupPermission(g, NodePermission.WRITE);
        permissions.addGroupPermissions(gp);
      }
    }
    return permissions;
  }

  @Override
  public Map<String, String> findAccessibleNodeIds() {
    return proxies.permission().findAccessibleNodeIds(getUserId());
  }
}