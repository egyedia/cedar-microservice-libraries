package org.metadatacenter.server.logging.dbmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "log_request")
public class ApplicationRequestLog {

  private static final Logger log = LoggerFactory.getLogger(ApplicationRequestLog.class);

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(length = 36)
  private String globalRequestId;

  @Column(length = 36)
  private String localRequestId;

  @Column(length = 50)
  private String systemComponentName;

  @Column(length = 50)
  private String type;

  @Column(length = 50)
  private String subType;

  @Column(length = 20)
  private String globalRequestIdSource;

  @Column(length = 255)
  private String userId;

  @Column(length = 255)
  private String clientSessionId;

  @Column(length = 32)
  private String jwtTokenHash;

  @Column(length = 20)
  private String authSource;

  private Instant requestTime;

  private Instant startTime;

  private Instant endTime;

  private long handlerDuration;

  private long preHandlerDuration;

  @Column(length = 1024)
  private String methodName;

  @Column(length = 1024)
  private String className;

  private int lineNumber;

  @Column(length = 20)
  private String httpMethod;

  @Column(length = 2048)
  private String path;

  @Column(length = 2048)
  private String queryParameters;

  public static ApplicationRequestLog fromAppRequestFilter(AppLogMessage appLog) {
    ApplicationRequestLog l = new ApplicationRequestLog();
    l.globalRequestId = appLog.getGlobalRequestId();
    l.localRequestId = appLog.getLocalRequestId();
    l.systemComponentName = appLog.getSystemComponent().getStringValue();
    l.requestTime = appLog.getLogTime();
    l.type = appLog.getType().getValue();
    l.subType = appLog.getSubType().getValue();
    l.globalRequestIdSource = appLog.getParamAsString(AppLogParam.GLOBAL_REQUEST_ID_SOURCE);
    l.httpMethod = appLog.getParamAsString(AppLogParam.HTTP_METHOD);
    l.path = appLog.getParamAsString(AppLogParam.PATH);
    Map<String, Object> parameters = appLog.getParamAsMap(AppLogParam.QUERY_PARAMETERS);
    try {
      l.queryParameters = JsonMapper.PRETTY_MAPPER.writeValueAsString(parameters);
    } catch (JsonProcessingException e) {
      log.error("Error serializing parameters", e);
    }
    return l;
  }

  public Long getId() {
    return id;
  }

  public void mergeStartLog(AppLogMessage appLog) {
    className = appLog.getParamAsString(AppLogParam.CLASS_NAME);
    methodName = appLog.getParamAsString(AppLogParam.METHOD_NAME);
    lineNumber = appLog.getParamAsInt(AppLogParam.LINE_NUMBER);
    userId = appLog.getParamAsString(AppLogParam.USER_ID);
    clientSessionId = appLog.getParamAsString(AppLogParam.CLIENT_SESSION_ID);
    jwtTokenHash = appLog.getParamAsString(AppLogParam.JWT_TOKEN_HASH);
    authSource = appLog.getParamAsString(AppLogParam.AUTH_SOURCE);
    startTime = appLog.getLogTime();
  }

  public void mergeEndLog(AppLogMessage appLog) {
    type = AppLogType.REQUEST_HANDLER.getValue();
    subType = AppLogSubType.FULL.getValue();
    endTime = appLog.getLogTime();
    if (startTime != null && endTime != null) {
      handlerDuration = Duration.between(startTime, endTime).toNanos();
    }
    if (requestTime != null && startTime != null) {
      preHandlerDuration = Duration.between(requestTime, startTime).toNanos();
    }
  }

}