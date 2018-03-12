package org.mule.extension;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.TestHttpClient;
import org.mule.tck.junit4.rule.DynamicPort;

import java.io.ByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;

public class RouteOperationTestCase extends MuleArtifactFunctionalTestCase {

  private static String PAYLOAD = "3 tristres tigres, tragaban trigo en un trigal, en tres tristes trasto, tragaban trigo tres tristes tigres.";

  @Rule
  public TestHttpClient httpClient = new TestHttpClient.Builder(getService(HttpService.class)).build();

  @Rule
  public DynamicPort dynamicPort = new DynamicPort("port");

  /**
   * Specifies the mule config xml with the flows that are going to be executed in the tests, this file lives in the test resources.
   */
  @Override
  protected String getConfigFile() {
    return "test-mule-config.xml";
  }

  @Test
  public void routeMeThisOne() throws Exception {

    HttpResponse response = httpClient.send(HttpRequest.builder()
                        .method("POST")
                        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(PAYLOAD.getBytes())))
                        .uri(String.format("http://localhost:%d/route", dynamicPort.getNumber()))
                      .build(), 10000, false, null);

    assertThat(IOUtils.toString(response.getEntity().getContent()), equalTo(PAYLOAD));
  }

}
