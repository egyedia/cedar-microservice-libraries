package org.metadatacenter.server.search.elasticsearch.worker;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.search.IndexedDocumentType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.metadatacenter.constant.ElasticsearchConstants.*;

public class ElasticsearchPermissionEnabledContentSearchingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchPermissionEnabledContentSearchingWorker.class);

  private final Client client;
  private final String indexName;
  private final CedarConfig cedarConfig;
  private final ElasticsearchConfig config;
  private final LinkedDataUtil linkedDataUtil;

  public ElasticsearchPermissionEnabledContentSearchingWorker(CedarConfig cedarConfig, Client client) {
    this.cedarConfig = cedarConfig;
    this.config = cedarConfig.getElasticsearchConfig();
    this.client = client;
    this.indexName = config.getIndexName();
    this.linkedDataUtil = cedarConfig.buildLinkedDataUtil();
  }

  public SearchResponseResult search(CedarRequestContext rctx, String query, List<String> resourceTypes, List<String>
      sortList, String templateId, int limit, int offset) throws CedarProcessingException {

    SearchRequestBuilder searchRequest = getSearchRequestBuilder(rctx, query, resourceTypes, sortList, templateId);

    searchRequest.setFrom(offset);
    searchRequest.setSize(limit);
    log.debug("Searching for parent documents: " + searchRequest.internalBuilder());

    // Execute request
    SearchResponse response = searchRequest.execute().actionGet();
    NodeIdResultList nodeIdResultList = new NodeIdResultList();
    nodeIdResultList.setTotalCount(response.getHits().getTotalHits());
    for (SearchHit hit : response.getHits()) {
      Map<String, Object> sourceMap = hit.sourceAsMap();
      nodeIdResultList.addId(hit.getId(), (String) sourceMap.get(ES_DOCUMENT_CEDAR_ID));
    }

    return searchContentWithIds(nodeIdResultList);
  }

  // It uses the scroll API. It retrieves all results. No pagination and therefore no offset. Scrolling is not
  // intended for real time user requests, but rather for processing large amounts of data.
  // More info: https://www.elastic.co/guide/en/elasticsearch/reference/2.3/search-request-scroll.html
  public SearchResponseResult searchDeep(CedarRequestContext rctx, String query, List<String> resourceTypes,
                                         List<String> sortList, String templateId, int limit, int offset) throws
      CedarProcessingException {

    SearchRequestBuilder searchRequest = getSearchRequestBuilder(rctx, query, resourceTypes, sortList, templateId);

    // Set scroll and scroll size
    TimeValue timeout = TimeValue.timeValueMinutes(2);
    searchRequest.setScroll(timeout);
    searchRequest.setSize(offset + limit);

    log.debug("Search query in Query DSL: " + searchRequest.internalBuilder());

    // Execute request
    SearchResponse response = searchRequest.execute().actionGet();

    NodeIdResultList nodeIdResultList = new NodeIdResultList();
    nodeIdResultList.setTotalCount(response.getHits().getTotalHits());
    int counter = 0;
    while (response.getHits().hits().length != 0) {
      for (SearchHit hit : response.getHits().hits()) {
        if (counter >= offset && counter < offset + limit) {
          Map<String, Object> sourceMap = hit.sourceAsMap();
          nodeIdResultList.addId(hit.getId(), (String) sourceMap.get(ES_DOCUMENT_CEDAR_ID));
        }
        counter++;
      }
      //next scroll
      response = client.prepareSearchScroll(response.getScrollId()).setScroll(timeout).execute().actionGet();
    }
    return searchContentWithIds(nodeIdResultList);
  }

  private SearchRequestBuilder getSearchRequestBuilder(CedarRequestContext rctx, String query, List<String>
      resourceTypes, List<String> sortList, String templateId) {

    SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName)
        .setTypes(IndexedDocumentType.NODE.getValue());

    BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();

    String userId = linkedDataUtil.getLinkedDataId(CedarNodeType.USER, rctx.getCedarUser().getId());

    // Filter by user
    QueryBuilder userChildQuery = QueryBuilders.hasChildQuery(IndexedDocumentType.USERS.getValue(),
        QueryBuilders.termQuery("users.id", userId)
    );

    mainQuery.must(userChildQuery);

    BoolQueryBuilder contentQuery = QueryBuilders.boolQuery();

    // Filter by content
    if (query != null && query.length() > 0) {
      QueryBuilder contentNameQuery = QueryBuilders.queryStringQuery(query).field(ES_RESOURCE_PREFIX +
          ES_RESOURCE_NAME_FIELD);
      contentQuery.must(contentNameQuery);
    }

    // Filter by resource type
    if (resourceTypes != null && resourceTypes.size() > 0) {
      QueryBuilder resourceTypesQuery = QueryBuilders.termsQuery(ES_RESOURCE_PREFIX + ES_RESOURCE_RESOURCETYPE_FIELD,
          resourceTypes);
      contentQuery.must(resourceTypesQuery);
    }

    // Filter by template id
    if (templateId != null) {
      QueryBuilder templateIdQuery = QueryBuilders.matchQuery(ES_TEMPLATEID_FIELD, templateId);
      contentQuery.must(templateIdQuery);
    }

    QueryBuilder contentChildQuery = QueryBuilders.hasChildQuery(IndexedDocumentType.CONTENT.getValue(),
        contentQuery
    );
    mainQuery.must(contentChildQuery);

    // Set main query
    searchRequestBuilder.setQuery(mainQuery);

    // Sort by field
    // Sort is not supported in parent-child queries, only with score function
    QueryBuilder contentChildScoreQuery = null;
    // The name is stored on the node, so we can sort by that
    if (sortList != null && sortList.size() > 0) {
      for (String s : sortList) {
        SortOrder sortOrder = SortOrder.ASC;
        if (s.startsWith(ES_SORT_DESC_PREFIX)) {
          sortOrder = SortOrder.DESC;
          s = s.substring(1);
        }
        if (ES_RESOURCE_SORT_NAME_FIELD.equals(s)) {
          searchRequestBuilder.addSort("name", sortOrder);
        } else if (ES_RESOURCE_SORT_LASTUPDATEDONTS_FIELD.equals(s)) {
          searchRequestBuilder.addSort("_score", sortOrder);
          QueryBuilder scoreQuery = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(new Script
              ("doc['info.lastUpdatedOnTS'].value")));
          contentChildScoreQuery = QueryBuilders.hasChildQuery(IndexedDocumentType.CONTENT.getValue(),
              scoreQuery
          ).scoreMode("max");
          mainQuery.must(contentChildScoreQuery);
        } else if (ES_RESOURCE_SORT_CREATEDONTS_FIELD.equals(s)) {
          searchRequestBuilder.addSort("_score", sortOrder);
        }
      }
    }
    return searchRequestBuilder;
  }

  private SearchResponseResult searchContentWithIds(NodeIdResultList nodeIdResultList) {
    QueryBuilder withParentIds = QueryBuilders.termsQuery("_parent", nodeIdResultList.getElasticIds());
    SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName)
        .setTypes(IndexedDocumentType.CONTENT.getValue())
        .setSize(nodeIdResultList.getCount());
    searchRequestBuilder.setQuery(withParentIds);

    log.debug("Searching for content documents: " + searchRequestBuilder.internalBuilder());

    return new SearchResponseResult(searchRequestBuilder.execute().actionGet(), nodeIdResultList);
  }

}