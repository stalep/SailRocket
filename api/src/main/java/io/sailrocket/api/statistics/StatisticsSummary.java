package io.sailrocket.api.statistics;

import java.io.PrintWriter;

public class StatisticsSummary {
   public final long startTime;
   public final long endTime;
   public final long minResponseTime;
   public final long meanResponseTime;
   public final long maxResponseTime;
   public final long[] percentileResponseTime; // the percentiles depend on configuration
   public final int connectFailureCount;
   public final int requestCount;
   public final int responseCount;
   public final int status_2xx;
   public final int status_3xx;
   public final int status_4xx;
   public final int status_5xx;
   public final int status_other;
   public final int resetCount;

   public StatisticsSummary(long startTime, long endTime, long minResponseTime, long meanResponseTime, long maxResponseTime,
                            long[] percentileResponseTime, int connectFailureCount, int requestCount, int responseCount,
                            int status_2xx, int status_3xx, int status_4xx, int status_5xx, int status_other, int resetCount) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.minResponseTime = minResponseTime;
      this.meanResponseTime = meanResponseTime;
      this.maxResponseTime = maxResponseTime;
      this.percentileResponseTime = percentileResponseTime;
      this.connectFailureCount = connectFailureCount;
      this.requestCount = requestCount;
      this.responseCount = responseCount;
      this.status_2xx = status_2xx;
      this.status_3xx = status_3xx;
      this.status_4xx = status_4xx;
      this.status_5xx = status_5xx;
      this.status_other = status_other;
      this.resetCount = resetCount;
   }

   public static void printHeader(PrintWriter writer, double[] percentiles) {
      writer.print("Requests,Responses,Mean,Min,");
      for (double p : percentiles) {
         writer.print('p');
         writer.print(p * 100);
         writer.print(',');
      }
      writer.print("Max,ConnFailure,Reset,2xx,3xx,4xx,5xx,Other");
   }

   public void printTo(PrintWriter writer) {
      writer.print(requestCount);
      writer.print(',');
      writer.print(responseCount);
      writer.print(',');
      writer.print(meanResponseTime);
      writer.print(',');
      writer.print(minResponseTime);
      writer.print(',');
      for (long prt : percentileResponseTime) {
         writer.print(prt);
         writer.print(',');
      }
      writer.print(maxResponseTime);
      writer.print(',');
      writer.print(connectFailureCount);
      writer.print(',');
      writer.print(resetCount);
      writer.print(',');
      writer.print(status_2xx);
      writer.print(',');
      writer.print(status_3xx);
      writer.print(',');
      writer.print(status_4xx);
      writer.print(',');
      writer.print(status_5xx);
      writer.print(',');
      writer.print(status_other);
   }
}
