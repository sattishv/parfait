package com.custardsource.parfait.timing;

import java.util.Random;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;

import com.custardsource.parfait.MonitorableRegistry;

// Sample code to try out the in-progress measurement stuff. To be replaced with a proper test case.
public class SampleRun {
    private static final Random RANDOM = new Random();
    public static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException, OpenDataException {
        BasicConfigurator.configure();
        ThreadMetricSuite suite = ThreadMetricSuite.blank();
        suite.addMetric(StandardThreadMetrics.CLOCK_TIME);
        suite.addMetric(StandardThreadMetrics.BLOCKED_TIME);
        suite.addMetric(StandardThreadMetrics.WAITED_TIME);
        suite.addMetric(StandardThreadMetrics.USER_CPU_TIME);
        EventTimer timer = new EventTimer("blah", new MonitorableRegistry(), suite, true, true);
        ThreadContext context = new ThreadContext();
        EmailSender sender = new EmailSender(context);
        CheckoutBuyer buyer = new CheckoutBuyer(context);
        timer.registerTimeable(sender, "sendEmail");
        timer.registerTimeable(buyer, "buySomething");

        Thread t1 = new Thread(sender);
        Thread t2 = new Thread(buyer);

        t1.start();
        t2.start();
        
        InProgressExporter exporter = new InProgressExporter(timer, context);
        
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(5000);
            
            TabularData data = exporter.captureInProgressMeasurements();
            System.out.println(tabToString(data));
        }

        t1.join();
        t2.join();

    }

    private static String tabToString(TabularData data) {
        String result = "";
        for (String column : data.getTabularType().getRowType().keySet()) {
            result += StringUtils.leftPad(column, 20) + "\t";
        }
        result += "\n";
        for (Object row : data.values()) {
            CompositeData rowData = (CompositeData) row;
            for (String column : data.getTabularType().getRowType().keySet()) {
                result += StringUtils.leftPad(rowData.get(column).toString(), 20) + "\t";
            }
            result += "\n";
            
        }
        return result;
    }

    public static abstract class FakeTask implements Timeable, Runnable {
        private EventTimer timer;
        private String action;
        protected ThreadContext context;
        private final Random random = new Random();

        public FakeTask(String action, ThreadContext context) {
            this.action = action;
            this.context = context;
        }

        @Override
        public void setEventTimer(EventTimer timer) {
            this.timer = timer;
        }

        @Override
        public void run() {
            EventMetricCollector collector = timer.getCollector();
            for (int i = 1; i < 30; i++) {
                try {
                    context.put("Name", randomName());
                    context.put("Company", randomCompany());
                    collector.startTiming(this, action);
                    doJob(i);
                    collector.pauseForForward();
                    collector.startTiming(this, "frog");
                    collector.stopTiming();
                    collector.resumeAfterForward();
                    collector.stopTiming();
                    context.remove("Name");
                    context.remove("Company");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                }
            }
        }

        private String randomCompany() {
            final String[] COMPANIES = new String[]{"ABC Corp", "Boople Inc", "Cabbages Pty Ltd", "Druggles MV"}; 
            return COMPANIES[random.nextInt(COMPANIES.length)];
        }

        private String randomName() {
            final String[] NAMES = new String[]{"Alex", "Betty", "Carlos", "Dietrich", "Edna"}; 
            return NAMES[random.nextInt(NAMES.length)];
        }

        protected abstract void doJob(int i) throws Exception;
    }

    public static class CheckoutBuyer extends FakeTask {
        public CheckoutBuyer(ThreadContext context) {
            super("buyItem", context);
        }

        @Override
        protected void doJob(int i) throws InterruptedException {
            if (i > 10 && i < 20) {
                synchronized (LOCK) {
                    Thread.sleep(RANDOM.nextInt(500) + 500);
                }
            } else {
                Thread.sleep(RANDOM.nextInt(500) + 500);
            }
        }
    }

    public static class EmailSender extends FakeTask {
        public EmailSender(ThreadContext context) {
            super("sendMail", context);
        }

        @Override
        protected void doJob(int i) throws Exception {
            synchronized (LOCK) {
                Thread.sleep(RANDOM.nextInt(400) + 400);
                if (i >= 25) {
                    for (int j = 0; j < 10000000; j++) {
                        System.out.print("");
                    }
                }
            }

        }
    }
}