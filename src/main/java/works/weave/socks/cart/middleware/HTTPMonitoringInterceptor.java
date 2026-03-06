package works.weave.socks.cart.middleware;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.webmvc.RepositoryRestHandlerMapping;
import org.springframework.data.rest.webmvc.support.JpaHelper;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class HTTPMonitoringInterceptor implements HandlerInterceptor {

    private static final String startTimeKey = "startTime";

    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    ResourceMappings mappings;
    @Autowired
    JpaHelper jpaHelper;
    @Autowired
    RepositoryRestConfiguration repositoryConfiguration;
    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    RequestMappingHandlerMapping requestMappingHandlerMapping;

    private Set<PatternsRequestCondition> urlPatterns;

    @Value("${spring.application.name:carts}")
    private String serviceName;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, Object o) throws Exception {
        httpServletRequest.setAttribute(startTimeKey, System.nanoTime());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
        long start = (long) httpServletRequest.getAttribute(startTimeKey);
        long elapsed = System.nanoTime() - start;
        String matchedUrl = getMatchingURLPattern(httpServletRequest);
        if (!matchedUrl.equals("")) {
            Timer.builder("http_request_duration_seconds")
                    .description("Request duration in seconds")
                    .tag("service", serviceName)
                    .tag("method", httpServletRequest.getMethod())
                    .tag("path", matchedUrl)
                    .tag("status_code", Integer.toString(httpServletResponse.getStatus()))
                    .register(meterRegistry)
                    .record(elapsed, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, Object o, Exception e) throws Exception {
    }

    private String getMatchingURLPattern(HttpServletRequest httpServletRequest) {
        String res = "";
        for (PatternsRequestCondition pattern : getUrlPatterns()) {
            if (pattern.getMatchingCondition(httpServletRequest) != null &&
                    !httpServletRequest.getServletPath().equals("/error")) {
                res = pattern.getMatchingCondition(httpServletRequest).getPatterns().iterator()
                        .next();
                break;
            }
        }
        return res;
    }

    private Set<PatternsRequestCondition> getUrlPatterns() {
        if (this.urlPatterns == null) {
            this.urlPatterns = new HashSet<>();
            requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) ->
                    urlPatterns.add(mapping.getPatternsCondition()));
            RepositoryRestHandlerMapping repositoryRestHandlerMapping = new
                    RepositoryRestHandlerMapping(mappings, repositoryConfiguration);
            repositoryRestHandlerMapping.setJpaHelper(jpaHelper);
            repositoryRestHandlerMapping.setApplicationContext(applicationContext);
            repositoryRestHandlerMapping.afterPropertiesSet();
            repositoryRestHandlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) ->
                    urlPatterns.add(mapping.getPatternsCondition()));
        }
        return this.urlPatterns;
    }
}