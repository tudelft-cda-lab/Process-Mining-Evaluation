package com.raffaeleconforti.benchmark;

import com.raffaeleconforti.measurements.impl.XFoldAlignmentBasedFMeasure;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XEventImpl;
import org.deckfour.xes.model.impl.XLogImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static com.raffaeleconforti.benchmark.Common.joinTrace;
import static com.raffaeleconforti.benchmark.Common.toTraces;

public class LogImporterFF {
    /**
     * Load a file in the flexfringe format to an XLog
     * @param factory unused, only here to follow a pattern
     * @param location Path to the  file
     * @return XLog from the flexfringe traces
     */
    public static XLog importFromFile(XFactory factory, String location) throws FileNotFoundException {
        if (!location.endsWith(".txt")) {
            throw new IllegalArgumentException("location is not a .txt file");
        }

        File logFile = new File(location);
        Scanner sc = new Scanner(logFile);
//        Read and skip first line
        sc.nextLine();


        XAttributeMap attributes = new XAttributeMapImpl(3);
        attributes.put("concept:name", new XAttributeLiteralImpl("concept:name", "XES Event Log"));
        XLog log = new XLogImpl(attributes);
        while (sc.hasNext()) {
            XTrace trace = factory.createTrace();
            String line = sc.nextLine();
            String[] parts = line.split(" ");
            for (int i = 2; i < parts.length; i++) {
                XAttributeMap eventAttributes = new XAttributeMapImpl(3);
                eventAttributes.put("concept:name", new XAttributeLiteralImpl("concept:name", parts[i]));
                eventAttributes.put("lifecycle:transition", new XAttributeLiteralImpl("lifecycle:transition", "complete"));
                eventAttributes.put("index", new XAttributeLiteralImpl("index", Integer.toString(i - 2)));
                trace.add(new XEventImpl(eventAttributes));
            }
            log.add(trace);
        }

        sc.close();

        return log;
    }
}
