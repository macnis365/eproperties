package net.jmatrix.eproperties;

import java.io.*;
import java.net.*;
import java.util.*;

import net.jmatrix.eproperties.cache.*;
import net.jmatrix.eproperties.parser.EPropertiesParser;
import net.jmatrix.eproperties.utils.URLUtil;

import org.apache.commons.logging.*;

/**
 * EProperties is the root properties object, and the core of the EProperties
 * system.  This object extends the java.util.Propertis object - which is a 
 * critical feature of the system.  Many many classes and projects in the 
 * Java community are initialized by methods/constructors that take 
 * Properties objects.  All of these subsystems and libraries can also be
 * initialized using the EProperties system, because an EProperties object
 * is a Properties object. <p>
 * 
 * 
 * Need to synchronize all mutators, along with the save operations.  Don't 
 * want someone writing while we are saving.  Write, save, and notification
 * operations should be atomic.   
 */
public class EProperties extends Properties implements Value<EProperties> {
   public static Log log=LogFactory.getLog(EProperties.class);
   
   /** When set to true, EProperties will emit some debug logging. */
   public static boolean debug=false;
   
   /** When set to true, eproperties will print warnings for potentially 
    * unresolved substitutions.  */
   public static boolean validateOnLoad=true;
   
   /** This is a formatting option for the 'save' function. */
   public static int INDENT_SPACES=3;
   
   /** An internal list of Key objects.  This maintains the order of the keys, 
    * useful for saving the properties object in the same order in which it 
    * was read. */
   private List<Key> keys = new ArrayList<Key>();
   
   // if this properties object is nested inside of another, then the
   // 'thisKey' is the value of the key that owns this object.  If this 
   // is the 'root' object, then thisKey is null;
   String thisKey=null;

   /** List of listeners... */
   List<PropertyListener> listeners = null;
   
   /** 
    * Root EProperties objects will have a source URL, corresponding to 
    * the file from which it was loaded.  If File A includes File B, then
    * File A will have a sourceURL, but not a includeURL.  File B will have a 
    * sourceURL AND an includeURL.
    */
   URL sourceURL=null;
   
   /** If this properties object is included via an include processor, 
    * then includeURL is set.  This is later used when writing properties
    * out.  If the includeURL at a particular level is non-null, then 
    * rather than writing the actual properties, it writes the include
    * url.  For example
    * foo=[http://bar.com/baz.properties]
    */
   String includeURL=null;

   /** Parent, if this object has a parent. */
   EProperties parent = null;
   
   /** */
   static CacheManager cacheManager=CacheManager.getInstance();
   
   /** lastModification is a unique number indicating the state of the source
    * at the last time load() was called.  Now - in the case of a File URL, 
    * this will simply be the last modification time.  <p>
    * 
    * This property is used to determine if the underlying property store 
    * has changed - ie, has the file, or http url, or database been modified, 
    * requiring a reload. <p>  
    * 
    * However, in a case 
    * where properties are in a database, we don't modification time.  So an 
    * alternate method may be chosen.  This could be as simple as a the hashcode
    * of a string composed of the concatenation of all the keys and values. 
    * How expensive this is depends on the relative proximity of the database, 
    * and the number of properties.  If such a calculation is expensive, 
    * the the monitoring thread should be slowed to an appropriate rate.  
    * */
   long lastModification=-1;
   
   public static boolean showVersion=true;
   private static volatile boolean versionShown=false;
   
   static synchronized final void showVersion() {
      if (showVersion == false)
         return;
      
      if (versionShown)
         return;
      
      versionShown=true;
      
      InputStream is=null;
      Properties p=null;
      try {
         is=EProperties.class.getResourceAsStream("version.properties");
         if (is != null) {
            p=new Properties();
            p.load(is);
         }
      } catch (Throwable t) {
         t.printStackTrace();
      } finally {
         if (is != null)
            try {is.close();} catch (Exception ex) {}
      }
      StringBuilder message=new StringBuilder();
      String version="UNKNOWN";
      if (p != null)
         version=p.getProperty("version");
      if (version == null)
         version="ERROR";
      //System.out.println ("version: "+version+" length: "+version.length());;
      int dlen=14;
      if (version.length() > dlen)
         version=version.substring(0,dlen);
      else if (version.length() < dlen) {
         int add=dlen-version.length();
         for (int i=0; i<add; i++)
            version=version+" ";
      }
      //System.out.println ("version: '"+version+"' version.length="+version.length());
      
      message.append("+-----------------------------------------------------------+\n");
      message.append("|         Loading EProperties version "+version   +"        |\n");
      message.append("| For more info, see the EProperties project on googlecode. |\n");
      message.append("|         http://code.google.com/p/eproperties/             |\n");
      message.append("+-----------------------------------------------------------+\n");

      System.out.println (message);
   }
   
   /**
    * Static initializer.  This simply prints some version and 
    * marketing info on the bootstrap of EProperties.
    */
   static {
//      InputStream is=null;
//      Properties p=null;
//      try {
//         is=EProperties.class.getResourceAsStream("version.properties");
//         if (is != null) {
//            p=new Properties();
//            p.load(is);
//         }
//      } catch (Throwable t) {
//         t.printStackTrace();
//      } finally {
//         if (is != null)
//            try {is.close();} catch (Exception ex) {}
//      }
//      StringBuilder message=new StringBuilder();
//      String version="UNKNOWN";
//      if (p != null)
//         version=p.getProperty("version");
//      if (version == null)
//         version="ERROR";
//      //System.out.println ("version: "+version+" length: "+version.length());;
//      int dlen=14;
//      if (version.length() > dlen)
//         version=version.substring(0,dlen);
//      else if (version.length() < dlen) {
//         int add=dlen-version.length();
//         for (int i=0; i<add; i++)
//            version=version+" ";
//      }
//      //System.out.println ("version: '"+version+"' version.length="+version.length());
//      
//      message.append("+-----------------------------------------------------------+\n");
//      message.append("|         Loading EProperties version "+version   +"        |\n");
//      message.append("| For more info, see the EProperties project on googlecode. |\n");
//      message.append("|         http://code.google.com/p/eproperties/             |\n");
//      message.append("+-----------------------------------------------------------+\n");
//
//      System.out.println (message);
   }
   
   
   /** Constructs a sub-properties object, the parent collection is passed
    * into the constructor. */
   public EProperties(EProperties par) {
      this();
      parent=par;
   }
   
   /** Empty constructor.  Most commonly used as a public API, with 
    * a load() operation called soon after.  */
   public EProperties() {
      showVersion();
   }
   
   /** Sets the URL from which this file was included. */
   public void setIncludedURL(String iurl) {
      includeURL=iurl;
   }
   
   public void setOwner(EProperties p) {
      this.parent=p;
   }
   
   /** 
    * Merges 2 property sets at the same level.  Any potentially duplicate
    * keys would be overwritten by the new values.
    * 
    * This operation will caused subsequent save() operations to write the 
    * merged proeprties.  
    * 
    * This is called by the parser to implement root property includes. 
    */
   public void merge(EProperties p) {
      log.debug("Merging "+p.size()+" new properties.  Current path is '"+getPath()+"'");
      log.debug("Merging, this.findSourceURL(): "+findSourceURL());
      log.debug("Merging, p.findSourceURL(): "+p.findSourceURL());
      
      List<Key> keys = p.getKeys();
      int size=keys.size();
      for (int i = 0; i < size; i++) {
         Key key = keys.get(i);

         // 21 jul 2009 - experimental.  I think we should merge the
         // pre-substitution values, not the derrived values.  
         
         //Object val = p.get(key); 
         Object val=p.preSubstitutionGet(key);
         
         if (val instanceof Value)
            ((Value)val).setOwner(this);
         
         this.put(key, val);
      }
   }
   
   /** 
    * Deep merge in more intelligent than merge.  Merge is draconian, 
    * totally overwriting any property (including potentially deeply 
    * nested properties) with the new value. 
    * 
    * Deep merge attempts to deal with nesting of properties.  Both strings 
    * and list values are still treated as atomic, and overwritten. But
    * deeper trees of properties a merged in a truer sense of the word.  It 
    * is more additive, with the merging properties taking precidece, but
    * not overwriting, rather merging deep trees.
    */
   public void deepMerge(EProperties p) {
      log.debug("Deep-Merging "+p.size()+" new properties.  Current path is '"+getPath()+"'");
      log.debug("Deep-Merging, this.findSourceURL(): "+findSourceURL());
      log.debug("Deep-Merging, p.findSourceURL(): "+p.findSourceURL());
      
      List<Key> keys=p.getKeys();
      for (Key key:keys) {
         String keyString=key.toString();
         
         Object val=p.preSubstitutionGet(keyString);
         
         log.debug("Deep-Merge: pre-substitution get returns "+
               (val == null? "null":(val.getClass().getName()+", "+val)));
         
         if (val instanceof EProperties) {
            EProperties mergingProps=(EProperties)val;
            
            Object existingVal=preSubstitutionGet(keyString);
            if (existingVal != null && existingVal instanceof EProperties) {
               EProperties existingProps=(EProperties)existingVal;
               existingProps.deepMerge(mergingProps);
            } else {
               this.put(keyString, val);
            }
         } else {
            //log.debug("Deep merge, pre-substitution value for key '"+key+"' is "+val);
            
            if (val instanceof Value) 
               ((Value)val).setOwner(this);
            this.put(keyString, val);
         }
      }
   }
   
   
   /** See flatten(String delim). */
   public EProperties flatten() {
      return flatten("|");
   }
   
   /** Flattens properties with the inbound delimiter. */
   public EProperties flatten(String delim) {
      return flatten("", delim);
   }
   
   /**
    * Flattens the property structure.  Nested properties are prefixed
    * with the name of the nesting structure.  For instance, if we have:
    * 
    * foo=bar
    * blah={
    *   bing=bat
    * }
    * 
    * Flatten would flatten the structure to:
    * foo=bar
    * blah|bing=bat
    * 
    * The delimiter is configurable (inbound on the method) with the 
    * default delimeter being the pipe character: '|'.
    * 
    * */
   protected EProperties flatten(String prefix, String delim) {
      EProperties flat=new EProperties();
      for (Key key:keys) {
         Object val=get(key);
         
         if (val == null) {
            log.warn("flatten: value is null for key "+key);
            if (!empty(prefix))
               flat.put(prefix+delim+key, val);
            else 
               flat.put(key.toString(), val);
         } else if (val instanceof EProperties) {
            EProperties epval=(EProperties)val;
            // recursion
            EProperties nestedFlat=null;
            if (!empty(prefix))
               nestedFlat=epval.flatten(prefix+delim+key.toString(), delim);
            else 
               nestedFlat=epval.flatten(key.toString(), delim);
            
            flat.addAll(nestedFlat);
         } else if (val instanceof List) {
            if (!empty(prefix))
               flat.put(prefix+delim+key, val.toString());
            else 
               flat.put(key.toString(), val.toString());
         } else {
            if (!empty(prefix))
               flat.put(prefix+delim+key, val.toString());
            else 
               flat.put(key.toString(), val.toString());
         }
      }
      return flat;
   }
   
   private static final boolean empty(String s) {
      if (s == null || s.length() == 0)
         return true;
      return false;
   }

   /** */
   public void addAll(Properties p) {
      Enumeration keyset=p.keys();
      while (keyset.hasMoreElements()) {
         Object key=keyset.nextElement();
         Object value=p.get(key);
         put(key, value);
      }
   }
   
   /** This method scans the properties tree, and returns a report of what
    * properties have un-resolved substitutions.  */
   public String validate() {
      int size=keys.size();
      
      StringBuilder sb=new StringBuilder();
      
      for (int i=0; i<size; i++) {
         Key key=keys.get(i);
         
         // substitutions should have happened at the point that
         // this is called.
         Object value=get(key);
         key.removeHit();
         if (value instanceof String) {
            String s=(String)value;
            if (SubstitutionProcessor.containsTokens(s)) {
               // this is an error.  Tokens should have been 
               // replaced by values here.
               sb.append("Warning: Value for key '"+key+"' defined in terms of "+
                     "un-resolvable substitution: '"+s+"'\n");
            }
         } else if (value instanceof List) {
            List<String> list=(List<String>)value;
            for (int j=0; j<list.size(); j++) {
               String lv=list.get(j);
               if (SubstitutionProcessor.containsTokens(lv)) {
                  sb.append("Warning: List item value for key '"+key+"["+j+"]' defined "+
                        "in terms of un-resolvable substitution: '"+lv+"'\n");
               }
            }
         } else if (value instanceof EProperties) {
            EProperties props=(EProperties)value;
            // maybe should indent here... 
            sb.append(props.validate());
         } else {
            // not really sure what this object is, if not one of the 
            // above types. I'm confused - but I'm not going to validate.
         }
      }
      return sb.toString();
   }
   
   public void superList() {
      super.list(new PrintStream(System.out));
   }
   
   public void list(PrintStream ps) {
      list((OutputStream)ps);
   }
   
   /** */
   public void list(OutputStream os) {
      list(os, false);
   }
   
   public void list(OutputStream ps, boolean hits) {
      list(new PrintWriter(new OutputStreamWriter(ps), true), 0, hits);
   }
   
   /** */
   public void list(PrintWriter pw) {
      list(pw,0);
   }
   
   public void list(PrintWriter pw, int depth) {
      list(pw, depth, false);
   }
   
   
   /** */
   public void list(PrintWriter pw, int depth, boolean printhits) {
      int size=keys.size();
      StringBuilder sb=new StringBuilder();
      for (int i=0; i<depth; i++) 
         sb.append("  ");
      String pad=sb.toString();
      
      for (int i=0; i<size; i++) {
         Key key=keys.get(i);
         Object value=get(key);
         key.removeHit();
         
         String keystring=null;
         if (printhits)
            keystring=key+"["+key.getHitCount()+"]";
         else
            keystring=key.toString();
         
         if (value instanceof EProperties) {
            pw.println(pad+keystring+" = {");
            ((EProperties)value).list(pw, depth+1, printhits);
            pw.println(pad+"}");
         } else if (value instanceof List) {
//            String s=value.toString();
//            // this will be a list starting and ending with []
//            // replace those with ()
//            s=s.substring(1, s.length()-1);
            List l=(List)value;
            StringBuilder sbb=new StringBuilder();
            for (Object item:l) {
               String sitem=item.toString();
               sitem=sitem.replace("\"", "\\\""); // escape quotes in list strings.
               sbb.append("\""+sitem+"\", ");
            }
            String s=sbb.toString();
            // trim the final , 
            s=s.substring(0, s.length()-2);
            
            pw.println (pad+keystring+"=("+s+")");
         }else {
            pw.println (pad+keystring+"="+value);
         }
      }
   }
   
   public String list() {
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      PrintWriter pw=new PrintWriter(new OutputStreamWriter(baos), true);
      list(pw);
      pw.flush();
      return baos.toString();
   }
   
   public String list(int depth) {
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      PrintWriter pw=new PrintWriter(new OutputStreamWriter(baos), true);
      list(pw, depth);
      pw.flush();
      return baos.toString();
   }
   ///////////////////////  Lineage  ////////////////////////////
   public void setParent(EProperties p, String key) {
      parent=p;
      thisKey=key;
      //System.out.println ("Set parent called with key '"+key+
      //      "', parent key is '"+p.thisKey+"' path is "+getPath());
   }
   
   /** */
   public String getPath() {
      StringBuilder sb=new StringBuilder();
      String key=thisKey;
      EProperties nextParent=parent;
      while (nextParent != null) {
         sb.insert(0, "->"+key);
         
         key=parent.thisKey;
         if (key == null)
            key="UNKNOWN";
         
         nextParent=nextParent.parent;
      }
      return sb.toString();
    }
   
   /**
    * This is the sole method for putting objects into the EProperties object.
    * 
    * This is synchronized to prevent concurrent access with load/read operations.
    */
   @Override
   public synchronized Object put(Object k, Object v) {
      if (k == null) {
         throw new NullPointerException("Key cannot be null in EProperties.");
      }
      Key key = null;
      // 
      if (k instanceof String) {
         key = new Key((String) k);
      } else if (k instanceof Key) {
         key = (Key) k;
      } else {
         throw new Error("Keys in EProperties must be [Key | String], not "+k.getClass().getName());
      }
      
      String keyString=key.toString();
      
      if (keyString.indexOf("->") != -1) {
         // this is a complex key.
         return putWithComplexKey(keyString, v);
         // FIXME: This does not account for listeners to keys/properties at any level
      }
      
      // Prevent duplicate keys, preserve order, notify listeners
      int keyIndex = keys.indexOf(key);
      if (keyIndex == -1)
         keys.add(key);
      else {
         //Key existingKey = keys.get(keys.indexOf(key));
         //existingKey.notifyListeners(v);
         key=keys.get(keyIndex); // replace created key w/ existing key
      }
      
      // build a value object.
      // There are 3 and only 3 types of values:
      //   1) String values.
      //   2) List Values, and
      //   3) EProperties values.
      // 
      // attempting to put any
      
      Object returnVal=v;
      Value value=null;
      
      if (v == null) {
         // Allowing us to call put(key, null). Setting a property to 
         // null is defined as removing that property.
         //super.put(key, v);
         super.remove(key);
      } else if (v instanceof String) {
         value=new StringValue((String)v, this);
         super.put(key, value);
      } else if (v instanceof List) {
         value=new ListValue((List)v, this);
         super.put(key, value);
      } else if (v instanceof EProperties) {
         super.put(key, v);
         
         // set parent.
         //setParent((EProperties)v, key.toString());
         ((EProperties)v).setParent(this, key.toString());
      } else if (v instanceof StringValue ||
                 v instanceof ListValue) {
         Value vv=(Value)v;
         
         log.debug("Putting raw StringValue or ListValue: persistent: "+
               vv.getPersistentValue()+" runtime: "+vv.getRuntimeValue());
         super.put(key, v);
      } else {
         throw new Error("EProperties put() values can only be: "+
               "[String | List<String> | EProperties | StringValue | ListValue].  "+
               "Not "+v.getClass().getName());
      }

      if (this.listeners != null) {
         notifyListeners(key, value);
      }
      // This will do nothing if there are no listeners.
      key.notifyListeners(returnVal);

      return returnVal;
   }
   
   /** 
    * putWithCompleKey() works much like getWithComplexKey().  I will give a 
    * simple example under 2 different conditions.
    * 
    * In both examples, the key is nested->foo.  In both cases the put method
    * has been called on a properties object we'll call 'root'.  
    * 
    * Case 1: The 'root' EProperties object already has a nested properties
    *         object named 'nested'.  In this case, we simply get a handle
    *         to the deeper properties object called 'nested' and on it
    *         we call put(key, val) with the key being a simple key 'foo' 
    *         and the value being the inbound value.  ALL BEHAVIORS OF PUT
    *         AT THAT POINT ARE IDENTICAL. Ie, if the value does not already 
    *         exist, it is added.  If the value does exist, IN ANY FORM - ie
    *         String, List or nested Properties - it is overwritten with 
    *         the inbound object value.
    *         
    * Case 2: The 'root' EProperties object does not already have a nested
    *         properties object called 'nested'.  In this case, a new properties
    *         object is created, named 'nested' and added to 'root'.  
    *         
    * For keys with more than 2 levels of depth (ie, a->b->c), the process
    * above is repeated until the last key (c in this example) is found - and 
    * that key represents the inbound value in the nested objects a->b that
    * either already exist, or are created by this method.
    */
   private synchronized Object putWithComplexKey(String key, Object value) {
      String keys[] = key.split("\\-\\>");
      
      log.debug ("getComplexKey(): "+Arrays.asList(keys));
      
      EProperties next = this;
      String currentPath = "";
      
      //StringBuilder path=new StringBuilder();
      
      for (int i = 0; i < keys.length - 1; i++) {
         // currentPath is used for debugging.
         EProperties prev=next;
         next=prev.getProperties(keys[i]);
         if (next == null) {
            // Create nested props that do not exist.
            next=new EProperties();
            prev.put(keys[i], next);
         }
      }
      
      return next.put(keys[keys.length-1], value);
   }

   /** */
   @Override
   public synchronized void putAll(Map m) {
      Iterator i=m.keySet().iterator();
      while (i.hasNext()) {
         Object key=i.next();
         Object val=m.get(key);
         put(key, val);
      }
   }

   /** */
   @Override
   public Object remove(Object k) {
      Key key = null;
      if (k instanceof String) {
         key = new Key((String) k);
      } else if (k instanceof Key) {
         key = (Key) k;
      } else {
         throw new Error("Keys in EProperties must be [Key, String].");
      }
      keys.remove(key);
     
      Object returnVal=super.remove(key);
      
      notifyListeners(key, returnVal);
      
      return returnVal;
   }
   
   /** Logs 'hits' to keys.  This allows us to know what properties are being
    * used in a long running JVM.  See Key.getHitCount(). */
   private void keyHit(Object key) {
      if (key instanceof String)
         key=new Key((String)key);
      int index=keys.indexOf(key);
      
      if (index < 0) {
         //System.out.println ("Can't log hit on "+key);
         //System.out.println ("keys: "+keys);
         //System.out.println ("keys.contains?"+keys.contains(key));
      } else {
         Key thekey=keys.get(index);
         thekey.hit();
         //System.out.println ("Logged hit on key "+thekey);
      }
   }
   
   /**
    * All getters funnel to this location.  This is the 'super' get.  
    */
   @Override
   public Object get(Object key) {
      Object val=null;
      
      if (key instanceof String) { 
         String skey=(String)key;
         if (skey.indexOf("->") > 0)
            val=getWithComplexKey(skey);
         else {
            val=super.get(key);
            if (val != null)
               keyHit(key);
         }
      } else { 
         val=super.get(key);
         if (val != null)
            keyHit(key);
      }
      if (debug)
         System.out.println ("get, key: '"+key+"', keyclass: "+key.getClass().getName());
      
      if (debug) {
         System.out.println ("get, val: '"+val+"' valClass: "+(val == null?"":val.getClass().getName()));
         if (val instanceof Value) {
            Value vv=(Value)val;
            System.out.println("   Value.getPersistent: '"+vv.getPersistentValue()+"'");
            
            System.out.println("   Value.getRuntime   : '"+vv.getRuntimeValue()+"'");
         }
      }
      
      // At this point, val could be null.  
      //System.out.println ("val: "+val);
      if (val == null)
         return val;
      
      // here, we'll process substitutions if they exist...
      if (val instanceof StringValue) {
         Object rtval=((StringValue)val).getRuntimeValue();
         
         if (rtval instanceof String &&
            SubstitutionProcessor.containsTokens((String)rtval)) {
            log.warn("Value for key '"+key+"' defined in terms of "+
                     "un-resolvable substitution: '"+rtval+"'");
         }
         return rtval;
      }
      else if (val instanceof ListValue) {
         List<String> list=((ListValue)val).getRuntimeValue();
         for (int j=0; j<list.size(); j++) {
            String lv=list.get(j);
            if (SubstitutionProcessor.containsTokens(lv)) {
               log.warn("List item value for key '"+key+"["+j+"]' defined "+
                     "in terms of un-resolvable substitution: '"+lv+"'\n");
            }
         }
         return list;
      }
      
      // this can and will be EProperties as well as primitives.
      return val;
   }
   
   private Object preSubstitutionGet(Object key) {
      log.debug("Pre-sub get "+key);
      return super.get(key);
   }
   
   /** */
   public Object internalGetValue(Object key) {
      // note, this key must be a Key, not a String
      return super.get(key);
   }
   
   /** */
   public EProperties internalResolveProperties(String path) {
      if (path.indexOf("->") == -1)
         return this;
      
      String keys[] = path.split("\\-\\>");
      
      log.debug ("getComplexKey(): "+Arrays.asList(keys));
      
      EProperties next = this;
      String currentPath = "";
      
      //StringBuilder path=new StringBuilder();
      
      for (int i = 0; i < keys.length - 1; i++) {
         // currentPath is used for debugging.
         if (currentPath.equals(""))
            currentPath = keys[i];
         else
            currentPath = currentPath + "->" + keys[i];

         Object nextTarget = next.get(keys[i]);
         if (nextTarget == null) {
            return null;
         } else if (nextTarget instanceof EProperties) {
            next = (EProperties) nextTarget;
         } else {
            log.debug("getComplexKey(): [" + path
                  + "]: Returning null because object at path "
                  + currentPath + " is not EProperties, it is "
                  + nextTarget.getClass().getName());
            return null;
         }
      }
      // here, we are pointed at the correct eproperties object.
      //return next.get(keys[keys.length-1]);
      return next;
   }
   
   /** */
   public Key internalResolveKey(String path) {
      EProperties p=null;
      String finalKey=null;
      if (path.indexOf("->") != -1) {
         p=internalResolveProperties(path); // can return null;
         
         String keyelements[]=path.split("\\-\\>");
         finalKey=keyelements[keyelements.length-1];
      } else {
         p=this;
         finalKey=path;
      }
      if (p == null)
         return null;
      List<Key> keys=p.getKeys();
      for (Key key:keys) {
         if (key.toString().equals(finalKey))
            return key;
      }
      return null;
   }
   
   /** */
   public Object get(String key, Object def) {
      Object ret = get(key);
      if (ret == null)
         return def;
      else
         return ret;
   }

   /** */
   public List<Key> getKeys() {
      return keys;
   }

   /** */
   public Object get(int keyNum) {
      return get(getKey(keyNum));
   }

   /** */
   public Key getKey(int i) {
      return keys.get(i);
   }

   /** Returns a list of keys that start with the input string. */
   public List<Key> getKeys(String s) {
      List<Key> prefixKeys = new ArrayList<Key>();
      int size = keys.size();
      for (int i = 0; i < size; i++) {
         Key key = keys.get(i);
         if (key.toString().startsWith(s))
            prefixKeys.add(key);
      }
      return prefixKeys;
   }
   
   @Override
   public synchronized Enumeration keys() {
      Vector v=new Vector();
      
      int size = keys.size();
      for (int i = 0; i < size; i++) {
         Key key = keys.get(i);
         v.add(key.toString());
      }
      return v.elements();
   }
   
   @Override
   public synchronized Collection values() {
      Vector v=new Vector();
      int size = keys.size();
      for (int i = 0; i < size; i++) {
         v.add(get(i));
      }
      return v;
   }
   
   @Override 
   public synchronized Enumeration elements() {
      return new VectorEnumerator((Vector)values());
   }
   
   public static class VectorEnumerator implements Enumeration {
      Vector v=null;
      int index=0;
      public VectorEnumerator(Vector v) {
         this.v=v;
      }
      public boolean hasMoreElements() {
         if (index < v.size())
            return true;
         return false;
      }
      public Object nextElement() {
         index++;
         return v.get(index-1);
      }
   }
   
   @Override
   public synchronized Set keySet() {
      HashSet hashSet=new HashSet();
      
      int size = keys.size();
      for (int i = 0; i < size; i++) {
         Key key = keys.get(i);
         hashSet.add(key.toString());
      }
      
      return hashSet;
   }
   
   @Override
   public Set<Map.Entry<Object, Object>> entrySet() {
      // This sucks to have to do, but Entry is a private class in 
      // hashmap.  And entrySet() is called fromn putAll().  Meaning I
      // MUST override this or people who copy this properties object
      // by constructing a new one, and calling putAll() and then assuming
      // that the keys are strings will fail. (Spring.  Spring sucks.)
      HashMap map=new HashMap();
      for (Key key:keys) {
         map.put(key.toString(), get(key));
      }
      return map.entrySet();
   }
   
   @Override
   public Enumeration<String> propertyNames() {
      return keys();
   }
   
   //@Override // this is an override only in 1.6
   public Set<String> stringPropertyNames() {
      return keySet();
   }
   
   @Override 
   public synchronized boolean containsKey(Object key) {
      Object val=get(key);
      if (val != null)
         return true;
      return false;
   }
   
   @Override
   public synchronized boolean contains(Object value) {
      if (value == null)
         return false;
      // inefficient, but functional.
      int size = keys.size();
      for (int i = 0; i < size; i++) {
         Object val=get(i);
         if (val.equals(value))
            return true;
      }
      return false;
   }
   
   @Override 
   public synchronized String toString() {
      return list();
   }

   ///////////////////////// Load/Save Methods ////////////////////////////
   /** This is the 'master' load() method.  All other load methods delegate
    * here for actual property loading.  */
   @Override
   public synchronized void load(InputStream is) 
   throws IOException {
      InputStream cis=cacheManager.getInputStream(is, sourceURL);
      
      EPropertiesParser parser=new EPropertiesParser(cis);
      parser.setSourceURL(sourceURL);

      try {
         parser.parse(this);
      } catch (Exception ex) {
         // this constructor is only available in Java 1.6
         //throw new IOException("Error parsing EProperties.", ex);
         ex.printStackTrace();
         throw new IOException("Error parsing Properties. "+ex.getMessage());
      } finally {
         if (cis != null) {
            try {
               cis.close();
            } catch (Exception clex) {
               clex.printStackTrace();
            }
         }
      }
      if (validateOnLoad)
         validate();
   }
   
//   @Override
//   public synchronized void load(Reader r) {
//      throw new RuntimeException("not implemented.");
//   }
   
   /**
    * Loads properties with a string representing either a URL or 
    * a file.  
    * 
    * @param surl
    */
   public void load(String surl) throws IOException {
      URL url=null;
      
      surl=URLUtil.convertClasspathURL(surl);
      
      String lcurl=surl.toLowerCase();
      
      //if (surl.indexOf("://") != -1) {
      if (lcurl.startsWith("http://") || lcurl.startsWith("https://") ||
          lcurl.startsWith("file:/") || lcurl.startsWith("jar:file:/")) {
         //System.out.println ("Constructing URL as URL");
         url=new URL(surl);
      } else {
         //System.out.println ("Constructing URL as File");
         url=(new File(surl)).toURI().toURL();
      }
      load(url);
   }
   
   /** */
   public synchronized void load(URL url) 
      throws IOException {
      setSourceURL(url);
      InputStream is=null;
      try {
         is=cacheManager.getInputStream(url);
         
         load(is);
         
//         if (is instanceof CacheInputStream) {
//            CacheInputStream cis=(CacheInputStream)is;
//            lastModification=cis.getLastModified();
//         }
//         //lastModification=tmp;
         
         lastModification=URLUtil.lastMod(url);
         log.debug("Last Mod is '"+lastModification+"'");
      } finally {
         if (is != null)
            is.close();
      }
   }
   
   /** */
   public boolean isSourceModified() {
      // FIXME, 18 AUG 2010 - possible NPE. sourceURL could be null.
      URL surl=sourceURL;
      if (surl == null) {
         surl=findSourceURL();
      }
      if (surl == null) 
         return false;
      
      try {
         //URLConnection con=sourceURL.openConnection();
         //URLConnection con=URLUtil.getConnection(surl);
         
         long lastMod=URLUtil.lastMod(surl);
         
         if (lastMod != lastModification) 
            return true;
      } catch (Exception ex) {
         log.error("Error checking source modification", ex);
         //ex.printStackTrace();
      }
      return false;
   }
   
   /** */
   public void load(File f) 
    throws IOException {
      load(f.toURI().toURL());
   }
   
   /** Reloads this EProperties object from its sourceURL. If this EProperties
    * object does not have a sourceURL, this method does nothing.
    */
   public void reload() 
      throws IOException {
      if (sourceURL != null) {
         load(sourceURL);
      } else {
         log.warn("reload() called on EProperties, but sourceURL is null.  noop.");
      }
   }
   
   /** */
   public URL getSourceURL() {
      URL url = sourceURL;
      if (url != null)
         return url;
      
      EProperties next = parent;
      while (next != null && url == null) {
         url = next.sourceURL;
         next = next.parent;
      }
      return url;
   }

   /** */
   public void setSourceURL(URL url) {
      log.debug("Setting source URL '"+url+"'");
      sourceURL = url; // new File(file.getAbsolutePath());
   }

   /** */
   public final URL findSourceURL() {
      EProperties props = this;
      URL url = sourceURL;
      while (url == null && props != null) {
         props = props.parent;
         if (props != null)
            url = props.sourceURL;
      }
      return url;
   }
   
   /** Save to the file currently identified by sourceURL.  sourceURL must be
    * file based - and not http based - we can't magically save to an 
    * http stream.
    * 
    * If this method is called on a properties object without a sourceURL, 
    * this method searches up the tree to find a properties object that
    * does have a source URL - and that object is saved if it can be found, 
    * and has a file:// based source url.
    * 
    * @return Returns true if a save has been executed, false if not possible.
    * @throws IOException
    */
   public boolean save() throws IOException {
      if (sourceURL != null) {
         if (sourceURL.getProtocol() != null
               && sourceURL.getProtocol().equals("file")) {

            File f = null;
            try {
               f = new File(sourceURL.toURI());
            } catch (Exception ex) {
               // this constructor is only available in Java 1.6
               //throw new IOException("Unable to form File from URL.", ex);
               ex.printStackTrace();
               throw new IOException("Unable to form File from URL. "+ex.getMessage());
            }
            save(f);
         } else {
            log.debug("Cannot save properties with URL " + sourceURL);
         }
         return true;
      } else {
         if (parent != null) {
            return parent.save();
         } else {
            return false;
         }
      }
   }
   
   public void save(File f) throws IOException {
      save(new FileWriter(f));
   }
   
   public void save(OutputStream os)
   throws IOException {
      save(new OutputStreamWriter(os));
   }
   
   public void save(Writer w) 
   throws IOException {
      save(w, 0);
   }
   
   /** */
   private synchronized void save(Writer w, int indent) 
      throws IOException {
      String pad="";
      if (indent > 0) {
         StringBuilder sb=new StringBuilder();
         for (int i=0; i<INDENT_SPACES*indent; i++) {
            sb.append(" ");
         }
         pad=sb.toString();
      }
      
      try {
         int size=keys.size();
         for (int i=0; i<size; i++) {
            Key key=keys.get(i);
            
            // writing key= is the same for string, list, properties, or
            // inclusion.
            
            // first, write comment lines if they exist
            String comments=key.getComments();
            if (comments != null) {
               // trim off trailing newline, then pad, then append newline
               comments=comments.trim(); 
               
               comments=pad+comments;
               comments=comments.replace("\n", "\n"+pad);
               comments=comments+"\n";
               
               w.write(comments);
            }
            w.write(pad+key.toString()+"=");
            
            // now, write the value, depending on its type.
            Object val=preSubstitutionGet(key);
            
            if (val == null) {
               w.write("\n"); // null is an unquoted empty line.
               // this will allow for the definition of a non-null
               // empty string as "".
            } else if (val instanceof StringValue) {
               String s=(String) ((StringValue)val).getPersistentValue();
               w.write("\""+s+"\"\n");
            } else if (val instanceof EProperties) {
               EProperties pval=(EProperties)val;
               
               if (pval.includeURL != null) {
                  // these properties were included.
                  w.write("["+pval.includeURL+"]\n");
               } else {
                  w.write("{\n");
                  pval.save(w, indent+1);
                  w.write(pad+"}\n");
               }
            } else if (val instanceof ListValue) {
               List list=(List)((ListValue)val).getPersistentValue();
               
               if (list.size() == 0) {
                  w.write("( )\n");
               } else {
                  
                  String keypad=pad(key.toString().length()+2, " ");
                  w.write("(");
                  int lsize=list.size();
                  for (int j=0; j<lsize; j++) {
                     w.write("\""+list.get(j)+"\"");
                     
                     if (j == lsize-1) // last element
                        w.write(")\n");
                     else 
                        w.write(", \n"+pad+keypad);
                  }
               }
            } else {
               log.error("What kind of value?? "+val.getClass().getName());
            }
            
            // append this newline for whitespace readability
            //if (i != size-1)
            //   w.write("\n");
         }
         // end of the for loop.
      } finally {
         if (w != null)
            w.flush();
      }
   }
   
   /** */
   public void save(String filename) 
      throws IOException {
      save(new FileOutputStream(filename));
   }
   
   private static final String pad(int pad, String padChar) {
      StringBuilder sb=new StringBuilder();
      for (int i=0; i<pad; i++) {
         sb.append(padChar);
      }
      return sb.toString();
   }
   
   /////////////////////////   Convience Put Methods  ////////////////////
   /** */
   public void putBoolean(String key, boolean value) {
      String val = null;
      if (value)
         val = "true";
      else
         val = "false";
      put(key, val);
   }

   /** */
   public void putInt(String key, int i) {
      String val = "" + i;
      put(key, val);
   }

   /** */
   public void putLong(String key, long l) {
      String val = "" + l;
      put(key, val);
   }
   
   @Override
   public Object setProperty(String k, String v) {
      return put(k, v);
   }
   
   /////////////////////////  Token Substitution  /////////////////////////
   /**
    * This method will search up a tree of EProoperties objects, looking for 
    * a match.  It will return the first match.
    * 
    * @param s
    * @return
    */
   public String findProperty(String s) {
      String val=getString(s, null);
      log.debug("findProperty(): Path='"+getPath()+"', "+s+"="+val);
            
      if (val != null)
         return val;
      else {
         if (parent != null) {
            return parent.findProperty(s);
         } else {
            log.debug("findProperty(): parent is null at Path='"+getPath()+"'");
         }
      }
      
      return val;
   }
   
   /**
    * This method will search up a tree of EProoperties objects, looking for 
    * a match.  It will return the first match.
    * 
    * @param s
    * @return
    */
   public Object findValue(String s) {
      Object val=get(s, null);
      log.debug("findValue(): Path='"+getPath()+"', "+s+"="+val);
            
      if (val != null)
         return val;
      else {
         if (parent != null) {
            return parent.findValue(s);
         } else {
            log.debug("findValue(): parent is null at Path='"+getPath()+"'");
         }
      }
      
      return val;
   }
   
   // /////////////////////// Accessor Methods ///////////////////////////
   public EProperties getProperties(Key key) {
      return getProperties(key.toString());
   }

   
   private Object getWithComplexKey(String key) {
      // a complex key uses a pointer syntax find properties
      // deeper in a structure.
      // for instance:
      // String s=(String)get("system->user.home");
      // is equivalent to
      // String s=(String)getProperties("system").get("user.home");
      //
      // Which brings up the issue of error handling. What if one of the
      // indirections returns a null nested property object!!
      // At that we simply return null - as if you requested a value 
      // from an EProperties object where the key does not exist.
      //
      // The final key can return any type of object (String, Vector,
      // Properties),
      // however all of the initial keys must return a nested EProperties
      // object. If they do not, then it returns null.
      String keys[] = key.split("\\-\\>");
      
      log.debug ("getComplexKey(): "+Arrays.asList(keys));
      
      EProperties next = this;
      String currentPath = "";
      
      //StringBuilder path=new StringBuilder();
      
      for (int i = 0; i < keys.length - 1; i++) {
         // currentPath is used for debugging.
         if (currentPath.equals(""))
            currentPath = keys[i];
         else
            currentPath = currentPath + "->" + keys[i];

         Object nextTarget = next.get(keys[i]);
         if (nextTarget == null) {
            return null;
         } else if (nextTarget instanceof EProperties) {
            next = (EProperties) nextTarget;
         } else {
            log.debug("getComplexKey(): [" + key
                  + "]: Returning null because object at path "
                  + currentPath + " is not EProperties, it is "
                  + nextTarget.getClass().getName());
            return null;
         }
      }
      // here, we are pointed at the correct eproperties object.
      return next.get(keys[keys.length-1]);
   }

   /** */
   @Override
   public String getProperty(String key) {
      return getProperty(key, null);
   }

   /** */
   @Override
   public String getProperty(String key, String def) {
      Object val = get(key);
      
      if (val == null)
         return def;
      if (val instanceof String)
         return (String)val;
      else
         return def;
   }

   /** */
   public String getString(String key) {
      return getProperty(key);
   }

   /** */
   public String getString(String key, String def) {
      return getProperty(key, def);
   }

   /** */
   public List<String> getList(String key) {
      Object val = get(key);
      if (val == null)
         return null;
      if (val instanceof List)
         return (List<String>)val;
      
      // Automagically convert strings to lists.  If the return 
      // value is a string, it will be parsed as a csv list.
      // this method is quite dumb.  First, any and all substitutions
      // are processed when retrieving the String, not in the list.
      // Second, if the value is a single string - with no commas, 
      // a single element list with the String as the only element 
      // is returned.
      if (val instanceof String) {
         List<String> l=convertStringToList((String)val);
         return l;
      }
      
      return null;
   }
   
   /** converts a string to a list. */
   private static final List<String> convertStringToList(String s) {
      List<String> l=new ArrayList<String>();
      String parsed[]=s.split("\\,");
      for (String p:parsed) 
         l.add(p);
      return l;
   }
   
   /** Returns a list with a default value if the list is null. **/
   public List<String> getList(String key, List<String>def) {
      List<String>rval=getList(key);
      if (rval != null)
         return rval;
      return def;
   }

   /** Returns a nested EProperties object if one is available for the
    * given key.  If not, it returns null. */
   public EProperties getProperties(String key) {
      Object val = get(key);
      if (val == null)
         return null;

      if (val instanceof EProperties)
         return (EProperties) val;
      else
         return null;
   }

   /** Returns a boolean.  Booleans are stored as Strings.  If the 
    * string representing the key begins with [t|T], then this
    * method returns true - otherwise it return false.  If the 
    * key is not defined (null as a string) then this method returns
    * false by default. */
   public boolean getBoolean(String key) {
      return getBoolean(key, false);
   }

   /** Boolean is a string.  If the string starts with [t | T] then 
    * this method returns true.  Otherwise, it returns false.  */
   public boolean getBoolean(String key, boolean def) {
      Object val = get(key);
      
      if (val == null)
         return def;
      if (val instanceof String) {
         String s = (String) val;
         s=s.toLowerCase().trim();
         if (s.startsWith("t"))
            return true;
         else
            return false;
      } else
         return def;
   }

   /** Returns an integer.  Integers are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to an integer, then this method returns -1.  */
   public int getInt(String key) {
      return getInt(key, -1);
   }

   /** Returns an integer.  Integers are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to an integer, then this method returns the default
    * value passed in.  */
   public int getInt(String key, int def) {
      Object val = get(key);
      if (val == null) {
         return def;
      }
      if (val instanceof String) {
         String s = (String)val;
         try {
            return Integer.parseInt(s.trim());
         } catch (NumberFormatException ex) {
            log.error("Cannot parse int from '" + s.trim()
                  + "', returning default of " + def);
            return def;
         }
      } else
         return def;
   }

   /** Returns a long.  Longs are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to a long, then this method returns -1.  */
   public long getLong(String key) {
      return getLong(key, -1);
   }

   /** Returns a long.  Longs are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to a long, then this method returns the default
    * value passed in.  */
   public long getLong(String key, long def) {
      Object val = get(key);
      if (val == null)
         return def;
      if (val instanceof String) {
         String s = (String)val;
         try {
            return Long.parseLong(s.trim());
         } catch (NumberFormatException ex) {
            log.error("Cannot parse long from " + s.trim()
                  + ", returning default of " + def);
            return def;
         }
      } else
         return def;
   }

   /** Returns a float.  Floats are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to a float, then this method returns 0.0f.  */
   public float getFloat(String key) {
      return getFloat(key, 0.0f);
   }

   /** Returns a float.  Floats are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to a float, then this method returns the defualt value
    * passed in.  */
   public float getFloat(String key, float def) {
      Object val = get(key);
      if (val == null) {
         return def;
      }
      if (val instanceof String) {
         String s = (String) val;
         try {
            return Float.parseFloat(s.trim());
         } catch (NumberFormatException ex) {
            log.error("Cannot parse float from '" + s.trim()
                  + "', returning default of " + def);
            return def;
         }
      } else
         return def;
   }

   /** Returns a double.  Double are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to a double, then this method returns 0.0f.  */
   public double getDouble(String key) {
      return getDouble(key, 0.0f);
   }

   /** Returns a double.  Double are stored as Strings.  If the 
    * string available with the inbound 'key' is null, or does not 
    * parse to a double, then this method returns the defualt value
    * passed in.  */
   public double getDouble(String key, double def) {
      Object val = get(key);
      if (val == null) {
         return def;
      }
      if (val instanceof String) {
         String s = (String) val;
         try {
            return Double.parseDouble(s.trim());
         } catch (NumberFormatException ex) {
            log.error("Cannot parse double from '" + s.trim()
                  + "', returning default of " + def);
            return def;
         }
      } else
         return def;
   }

   // ///////////////////// Listener Infrastructure //////////////////////
   /** */
   public void addListener(PropertyListener listener) {
      if (listeners == null)
         listeners = new ArrayList<PropertyListener>();
      listeners.remove(listener); // prevent duplicates
      listeners.add(listener);
   }

   /** */
   public void removeListener(PropertyListener obs) {
      if (listeners != null)
         listeners.remove(obs);
   }

   /** */
   void notifyListeners(Key key, Object value) {
      if (listeners == null)
         return;

      // generate the event
      PropertyChangeEvent evt = new PropertyChangeEvent(key, value);

      for (int i = 0; i < listeners.size(); i++) {
         PropertyListener obs = listeners.get(i);
         obs.propertyChange(evt);
      }
   }

   @Override
   public EProperties getPersistentValue() {
      return this;
   }

   @Override
   public Object getRuntimeValue() {
      return this;
   }
}
