package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderGroup;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderNode;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderGroup;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

import java.util.List;
import java.util.Map;

public class Neo4JProxyCategory extends AbstractNeo4JProxy {

  Neo4JProxyCategory(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  public FolderServerCategory getRootCategory() {
    String cypher = CypherQueryBuilderCategory.getRootCategory();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public FolderServerCategory createCategory(CedarCategoryId parentCategoryId, CedarCategoryId newCategoryId,
                                             String categoryName,
                                             String categoryDescription, String categoryIdentifier, String userId) {
    String cypher = CypherQueryBuilderCategory.createCategory(parentCategoryId, userId);
    CypherParameters params = CypherParamBuilderCategory.createCategory(parentCategoryId, newCategoryId, categoryName,
        categoryDescription, categoryIdentifier, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerCategory.class);
  }

  public FolderServerCategory getCategoryByParentAndName(CedarCategoryId parentId, String name) {
    String cypher = CypherQueryBuilderCategory.getCategoryByParentAndName();
    CypherParameters params = CypherParamBuilderCategory.getCategoryByParentAndName(parentId, name);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public FolderServerCategory getCategoryById(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.getCategoryById();
    CypherParameters params = CypherParamBuilderNode.getNodeById(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public List<FolderServerCategory> getAllCategories(int limit, int offset) {
    String cypher = CypherQueryBuilderCategory.getAllCategories();
    CypherParameters params = CypherParamBuilderCategory.getAllCategories(limit, offset);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerCategory.class);
  }

  public long getCategoryCount() {
    String cypher = CypherQueryBuilderCategory.getTotalCount();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetCount(q);
  }

  public FolderServerCategory updateCategoryById(CedarCategoryId categoryId, Map<NodeProperty, String> updateFields,
                                                 String updatedBy) {
    String cypher = CypherQueryBuilderGroup.updateCategoryById(updateFields);
    CypherParameters params = CypherParamBuilderGroup.updateCategoryById(categoryId, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerCategory.class);
  }

  public boolean deleteCategoryById(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.deleteCategoryById();
    CypherParameters params = CypherParamBuilderCategory.deleteCategoryById(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "deleting category");
  }

  public FolderServerUser getCategoryOwner(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.getCategoryOwner();
    CypherParameters params = CypherParamBuilderNode.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerUser.class);
  }

  public boolean attachCategoryToArtifact(CedarCategoryId categoryId, String artifactId) {
    String cypher = CypherQueryBuilderCategory.attachCategoryToArtifact();
    CypherParameters params = CypherParamBuilderGroup.categoryIdAndArtifactId(categoryId, artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerCategory category = executeWriteGetOne(q, FolderServerCategory.class);
    return category != null;
  }

  public boolean detachCategoryFromArtifact(CedarCategoryId categoryId, String artifactId) {
    String cypher = CypherQueryBuilderCategory.detachCategoryFromArtifact();
    CypherParameters params = CypherParamBuilderGroup.categoryIdAndArtifactId(categoryId, artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerCategory category = executeWriteGetOne(q, FolderServerCategory.class);
    return category != null;
  }

  public List<FolderServerCategory> getCategoryPaths(String artifactId) {
    String cypher = CypherQueryBuilderCategory.getCategoryPathsByArtifactId();
    CypherParameters params = CypherParamBuilderNode.getNodeById(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerCategory.class);
  }

  public void updateCategoryOwner(CedarCategoryId categoryId, String newOwnerId) {
    FolderServerUser user = proxies.user().findUserById(newOwnerId);
    if (user != null) {
      FolderServerCategory category = proxies.category().getCategoryById(categoryId);
      if (category != null) {
        proxies.category().updateOwner(category, user);
      }
    }
  }

  private boolean setOwner(FolderServerCategory category, FolderServerUser user) {
    String cypher = CypherQueryBuilderCategory.setCategoryOwner();
    CypherParameters params = CypherParamBuilderNode.matchNodeAndUser(category.getId(), user.getId());
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting owner");
  }

  private boolean removeOwner(FolderServerCategory category) {
    String cypher = CypherQueryBuilderCategory.removeCategoryOwner();
    CypherParameters params = CypherParamBuilderNode.matchNodeId(category.getId());
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing owner");
  }

  boolean updateOwner(FolderServerCategory category, FolderServerUser user) {
    boolean removed = removeOwner(category);
    if (removed) {
      return setOwner(category, user);
    }
    return false;
  }

  public List<FolderServerCategory> getCategoryPath(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.getCategoryPath();
    CypherParameters params = CypherParamBuilderNode.getNodeById(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerCategory.class);
  }
}