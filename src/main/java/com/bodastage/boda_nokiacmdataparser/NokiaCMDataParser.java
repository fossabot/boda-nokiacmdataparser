package com.bodastage.boda_nokiacmdataparser;

/**
 * Bodastage Solutions
 *
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class NokiaCMDataParser 
{
    
    /**
     * Tracks Managed Object attributes to write to file. This is dictated by 
     * the first instance of the MO found. 
     * @TODO: Handle this better.
     *
     * @since 1.0.0
     */
    private Map<String, Stack> moColumns = new LinkedHashMap<String, Stack>();
    
    /**
     * This holds a map of the Managed Object Instances (MOIs) to the respective
     * csv print writers.
     * 
     * @since 1.0.0
     */
    private Map<String, PrintWriter> moiPrintWriters 
            = new LinkedHashMap<String, PrintWriter>();
    
    /**
     * Tag data.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    private String tagData = "";
    
    /**
     * Output directory.
     *
     * @since 1.0.0
     */
    private String outputDirectory = "/tmp";
    
    /**
     * Parser start time. 
     * 
     * @since 1.0.4
     * @version 1.0.0
     */
    final long startTime = System.currentTimeMillis();
    
    /**
     * The base file name of the file being parsed.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    private String baseFileName = "";
    
    /**
     * The file to be parsed.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    private String dataFile;
    
    /**
     * The holds the parameters and corresponding values for the moi tag  
     * currently being processed.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    private Map<String,String> moiParameterValueMap 
            = new LinkedHashMap<String, String>();
    
    
    
    /**
     * List of parameters and their values in the managedObject>list>item location
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    private Map<String,String> itemParamValueMap 
            = new LinkedHashMap<String, String>();
    
    /**
     * Current className MO attribute.
     * 
     * @since 1.0.0
     * @version 1.0.0
    */
    private String moClassName = null;
    private String moDistName = null;
    private String moVersion = null;
    private String moId = null;
    
    private boolean inItem = false;
    private boolean inHead = false;
    
    private String listName = null;
    private String dateTime = null;
    private String parameterName = null;
    
    /**
     * Value of the name atttribute of the p XML tag
     * 
     * @since 1.0.0
     */
    private String pAttrName = null;

    
    public static void main( String[] args )
    {
        try{
            //show help
            if(args.length != 2 || (args.length == 1 && args[0] == "-h")){
                showHelp();
                System.exit(1);
            }
            //Get bulk CM XML file to parse.
            String filename = args[0];
            String outputDirectory = args[1];
            
            //Confirm that the output directory is a directory and has write 
            //privileges
            File fOutputDir = new File(outputDirectory);
            if(!fOutputDir.isDirectory()) {
                System.err.println("ERROR: The specified output directory is not a directory!.");
                System.exit(1);
            }
            
            if(!fOutputDir.canWrite()){
                System.err.println("ERROR: Cannot write to output directory!");
                System.exit(1);            
            }

            NokiaCMDataParser parser = new NokiaCMDataParser();
            parser.setFileName(filename);
            parser.setOutputDirectory(outputDirectory);
            parser.parse();
            parser.printExecutionTime();
        }catch(Exception e){
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
    
    
    /**
     * Show parser help.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    static public void showHelp(){
        System.out.println("boda-nokiacmdataparser 1.0.0. Copyright (c) 2017 Bodastage(http://www.bodastage.com)");
        System.out.println("Parses Nokia configuration configuration data file XML to csv.");
        System.out.println("Usage: java -jar boda-nokiacmdataparser.jar <fileToParse.xml> <outputDirectory>");
    }
    
    
    /**
     * The parser's entry point.
     * 
     */
    public void parse() 
    throws XMLStreamException, FileNotFoundException, UnsupportedEncodingException
    {
            XMLInputFactory factory = XMLInputFactory.newInstance();

            XMLEventReader eventReader = factory.createXMLEventReader(
                    new FileReader(this.dataFile));
            baseFileName = getFileBasename(this.dataFile);

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                
                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        startElementEvent(event);
                        break;
                    case XMLStreamConstants.SPACE:
                    case XMLStreamConstants.CHARACTERS:
                        characterEvent(event);
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        endELementEvent(event);
                        break;
                    case XMLStreamConstants.COMMENT:
                        break;
                }
            }
            //
            closeMOPWMap();
    }
    
    /**
     * Handle start element event.
     *
     * @param xmlEvent
     *
     * @since 1.0.0
     * @version 1.0.0
     *
     */
    public void startElementEvent(XMLEvent xmlEvent) throws FileNotFoundException {
        
        StartElement startElement = xmlEvent.asStartElement();
        String qName = startElement.getName().getLocalPart();
        String prefix = startElement.getName().getPrefix();
        
        Iterator<Attribute> attributes = startElement.getAttributes();
        //CHeck beginning of the head tag
        if(qName.equals("header")){
            inHead = true;
        }
        
        //Extract the dateTime from the log tag
        if(qName.equals("log") && inHead == true ){
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                String attrName = attribute.getName().getLocalPart();
                String attrValue =  attribute.getValue();
                if (attrName.equals("dateTime")) {
                    dateTime = attrValue;
                }
            }
        }
        
        //Extract managedObject
        if(qName.equals("managedObject") ){
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                String attrName = attribute.getName().getLocalPart();
                String attrValue =  attribute.getValue();
                if (attrName.equals("class")) {
                    moClassName = attrValue;
                }
                if (attrName.equals("version")) {
                    moVersion = attrValue;
                }
                if (attrName.equals("distName")) {
                    moDistName = attrValue;
                }
                if (attrName.equals("id")) {
                    moId = attrValue;
                }
            }
        }
        
        //list tag
        if(qName.equals("list") ){
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                String attrName = attribute.getName().getLocalPart();
                String attrValue =  attribute.getValue();
                if (attrName.equals("name")) {
                    listName = attrValue;
                }
            }
        }
        
        //list item tag
        if(qName.equals("item")){
            inItem = true;
        }
        
        //parameter tag
        if(qName.equals("p") ){
            while (attributes.hasNext()) {
                parameterName = null;
                Attribute attribute = attributes.next();
                String attrName = attribute.getName().getLocalPart();
                String attrValue =  attribute.getValue();
                if (attrName.equals("name")) {
                    parameterName = attrValue;
                }
            }
        }
    }
    
    /**
     * Handle character events.
     *
     * @param xmlEvent
     * 
     * @version 1.0.0
     * @since 1.0.0
     */
    public void characterEvent(XMLEvent xmlEvent) {
        Characters characters = xmlEvent.asCharacters();
        if(!characters.isWhiteSpace()){
            tagData = characters.getData(); 
        }
    }  
    
    /**
     * Get file base name.
     * 
     * @param filename String The base name of the input data file.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
     public String getFileBasename(String filename){
        try{
            return new File(filename).getName();
        }catch(Exception e ){
            return filename;
        }
    }
     
     
    /**
     * Processes the end tags.
     * 
     * @param xmlEvent
     * 
     * @since 1.0.0
     * @version 1.0.0
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException 
     */
    public void endELementEvent(XMLEvent xmlEvent)
            throws FileNotFoundException, UnsupportedEncodingException {
        EndElement endElement = xmlEvent.asEndElement();
        String prefix = endElement.getName().getPrefix();
        String qName = endElement.getName().getLocalPart();
        
        if(qName.equals("head")){
            inHead = false;
        }
        
        if(qName.equals("p") && listName == null){
            moiParameterValueMap.put(parameterName, tagData );
            parameterName = null;
        }
        
        if(qName.equals("p") && listName != null && parameterName == null && 
                inItem == false){
            if( moiParameterValueMap.containsKey(listName)){
                String prevValue = moiParameterValueMap.get(listName);
                moiParameterValueMap.put(listName, prevValue + ";" + tagData);
            }else{
                moiParameterValueMap.put(listName, tagData);
            }
            
            parameterName = null;
        }
        
        if(qName.equals("p") && listName != null && inItem == true && 
                parameterName != null ){
            if(itemParamValueMap.containsKey(parameterName)){
                String prevValue = itemParamValueMap.get(parameterName);
                itemParamValueMap.put(parameterName, prevValue + ";" + tagData );
            }else{
                itemParamValueMap.put(parameterName, tagData);
            }
            
            parameterName = null;
        }
        
        if(qName.equals("managedObject")){
            //System.out.println("managedObject:" + moClassName);
            String paramNames = "FileName,dateTime,version,distName,id";
            String paramValues = baseFileName+ "," + dateTime + ","+moVersion+","+moDistName+","+moId;
            
            if( !moColumns.containsKey(moClassName)){
                
                String moiFile = outputDirectory + File.separatorChar + moClassName +  ".csv";
                moiPrintWriters.put(moClassName, new PrintWriter(moiFile));
                
                Iterator<Map.Entry<String, String>> iter = 
                        moiParameterValueMap.entrySet().iterator();
                Stack columns = new Stack();
                while(iter.hasNext()){
                    Map.Entry<String, String> me = iter.next();
                    columns.add(me.getKey());
                    paramNames += "," + me.getKey();
                }
                
                moColumns.put(moClassName, columns);
                moiPrintWriters.get(moClassName).println(paramNames);
            }
                
            Iterator<Map.Entry<String, String>> iter = 
                        moiParameterValueMap.entrySet().iterator();
            Stack columns = moColumns.get(moClassName);
            
            for(int i=0; i < columns.size(); i++){
                String pName = columns.get(i).toString();
                if( moiParameterValueMap.containsKey(pName)){
                    paramValues += "," + toCSVFormat(moiParameterValueMap.get(pName));
                }else{
                    paramValues += ",";
                }
            }
            
            PrintWriter pw = moiPrintWriters.get(moClassName);
            pw.println(paramValues);
            
            moiParameterValueMap.clear();
            moClassName = null;
        }
        
        if(qName.equals("item")){
            Iterator<Map.Entry<String, String>> iter = itemParamValueMap.entrySet().iterator();
            String pName = listName + "_";
            String pValue = "";
            while(iter.hasNext()){
                Map.Entry<String, String> me = iter.next();
                pName += me.getKey() + "_";
                pValue+= me.getValue() + "&";
            }
            
            pValue = pValue.replaceAll("&$", "");
            pName = pName.replaceAll("_$", "");
            
            if( moiParameterValueMap.containsKey(pName)){
                String prevValue = moiParameterValueMap.get(pName);
                moiParameterValueMap.put(pName, prevValue + ";" + pValue);
            }else{
                moiParameterValueMap.put(pName, pValue);
            }
            inItem = false;
            itemParamValueMap.clear();
        }
        
        if(qName.equals("list")){
            listName = null; 
        }
        
        
    }
    
    
    /**
     * Print program's execution time.
     * 
     * @since 1.0.0
     */
    public void printExecutionTime(){
        float runningTime = System.currentTimeMillis() - startTime;
        
        String s = "Parsing completed. ";
        s = s + "Total time:";
        
        //Get hours
        if( runningTime > 1000*60*60 ){
            int hrs = (int) Math.floor(runningTime/(1000*60*60));
            s = s + hrs + " hours ";
            runningTime = runningTime - (hrs*1000*60*60);
        }
        
        //Get minutes
        if(runningTime > 1000*60){
            int mins = (int) Math.floor(runningTime/(1000*60));
            s = s + mins + " minutes ";
            runningTime = runningTime - (mins*1000*60);
        }
        
        //Get seconds
        if(runningTime > 1000){
            int secs = (int) Math.floor(runningTime/(1000));
            s = s + secs + " seconds ";
            runningTime = runningTime - (secs/1000);
        }
        
        //Get milliseconds
        if(runningTime > 0 ){
            int msecs = (int) Math.floor(runningTime/(1000));
            s = s + msecs + " milliseconds ";
            runningTime = runningTime - (msecs/1000);
        }

        
        System.out.println(s);
    }
    
    /**
     * Close file print writers.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    public void closeMOPWMap() {
        Iterator<Map.Entry<String, PrintWriter>> iter
                = moiPrintWriters.entrySet().iterator();
        while (iter.hasNext()) {
            iter.next().getValue().close();
        }
        moiPrintWriters.clear();
    }
    
    /**
     * Process given string into a format acceptable for CSV format.
     *
     * @since 1.0.0
     * @param s String
     * @return String Formated version of input string
     */
    public String toCSVFormat(String s) {
        String csvValue = s;

        //Check if value contains comma
        if (s.contains(",")) {
            csvValue = "\"" + s + "\"";
        }

        if (s.contains("\"")) {
            csvValue = "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return csvValue;
    }
    
    /**
     * Set the output directory.
     * 
     * @since 1.0.0
     * @version 1.0.0
     * @param String directoryName 
     */
    public void setOutputDirectory(String directoryName ){
        this.outputDirectory = directoryName;
    }
     
    /**
     * Set name of file to parser.
     * 
     * @since 1.0.0
     * @version 1.0.0
     * @param String filename
     */
    public void setFileName(String filename ){
        this.dataFile = filename;
    }
    
}
