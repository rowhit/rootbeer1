/* 
 * Copyright 2012 Phil Pratt-Szeliga and other contributors
 * http://chirrup.org/
 * 
 * See the file LICENSE for copying permission.
 */

package edu.syr.pcpratts.rootbeer.classloader;

import edu.syr.pcpratts.rootbeer.generate.bytecode.MultiDimensionalArrayTypeCreator;
import java.util.*;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class DfsInfo {

  private Set<String> m_dfsMethods;
  private Set<Type> m_dfsTypes;
  private CallGraph m_callGraph;
  private Map<String, List<Type>> m_parentsToChildren;
  private Map<String, List<NumberedType>> m_childrenToParents;
  private Map<String, List<NumberedType>> m_hierarchyDown;
  private Map<String, NumberedType> m_numberedTypeMap;
  private List<NumberedType> m_numberedTypes;
  private List<Type> m_orderedTypes;
  private List<RefType> m_orderedRefTypes;
  private List<Type> m_orderedRefLikeTypes;
  private Set<SootField> m_dfsFields;
  private Set<ArrayType> m_arrayTypes;
  private List<Type> m_builtInTypes;
  private Map<SootClass, Integer> m_classToNumber;
  private Set<Type> m_instanceOfs;
  private List<String> m_reachableMethodSigs;
  
  public DfsInfo() {
    m_dfsMethods = new HashSet<String>();
    m_dfsTypes = new HashSet<Type>();
    m_dfsFields = new HashSet<SootField>();
    m_callGraph = new CallGraph();
    m_builtInTypes = new ArrayList<Type>();
    m_instanceOfs = new HashSet<Type>();
    m_reachableMethodSigs = new ArrayList<String>();
    m_parentsToChildren = new HashMap<String, List<Type>>();
    addBuiltInTypes();
  }
  
  public void expandArrayTypes(){  
    m_arrayTypes = new HashSet<ArrayType>();
    for(Type type : m_dfsTypes){
      if(type instanceof ArrayType){
        ArrayType array_type = (ArrayType) type;
        m_arrayTypes.add(array_type);
      }
    }
    
    MultiDimensionalArrayTypeCreator creator = new MultiDimensionalArrayTypeCreator();
    Set<ArrayType> added = creator.create(m_arrayTypes);
    m_dfsTypes.addAll(added);
    m_arrayTypes.addAll(added);
    SootClass obj_class = Scene.v().getSootClass("java.lang.Object");
    for(Type added_type : m_arrayTypes){
      addSuperClass(added_type, obj_class.getType());
    }
  }
  
  public void orderTypes(){
    m_numberedTypeMap = new HashMap<String, NumberedType>();
    m_classToNumber = new HashMap<SootClass, Integer>();
    
    List<NumberedType> numbered_types = new ArrayList<NumberedType>();
    int number = 1;
    List<Type> queue = new LinkedList<Type>();
    queue.addAll(m_builtInTypes);
    while(queue.isEmpty() == false){
      Type curr = queue.get(0);
      queue.remove(0);
      
      NumberedType numbered_type = new NumberedType(curr, number);
      numbered_types.add(numbered_type);
      m_numberedTypeMap.put(curr.toString(), numbered_type);
      if(curr instanceof RefType){
        RefType ref_type = (RefType) curr;
        SootClass curr_class = ref_type.getSootClass();
        m_classToNumber.put(curr_class, number);
      }
      
      number++;
      
      if(m_parentsToChildren.containsKey(curr.toString()) == false){
        continue;
      }
      
      List<Type> children = m_parentsToChildren.get(curr.toString());
      queue.addAll(children);
    }
    
    m_numberedTypes = new ArrayList<NumberedType>();
    m_orderedTypes = new ArrayList<Type>();
    m_orderedRefTypes = new ArrayList<RefType>();
    m_orderedRefLikeTypes = new ArrayList<Type>();
    for(int i = numbered_types.size() - 1; i >= 0; --i){
      NumberedType curr2 = numbered_types.get(i);
      m_numberedTypes.add(curr2);
      m_orderedTypes.add(curr2.getType());
      
      Type type = curr2.getType();
      if(type instanceof RefType){
        RefType ref_type = (RefType) type;
        m_orderedRefTypes.add(ref_type);
      } 
      if(type instanceof RefLikeType){
        m_orderedRefLikeTypes.add(type);
      }
    }
  }
  
  public void createClassHierarchy(){
    m_childrenToParents = new HashMap<String, List<NumberedType>>();
    for(Type type : m_dfsTypes){
      List<NumberedType> parents = new ArrayList<NumberedType>();
      NumberedType curr_type = m_numberedTypeMap.get(type.toString());
      parents.add(curr_type);
      if(type instanceof RefType){
        RefType ref_type = (RefType) type;
        SootClass curr_class = ref_type.getSootClass();
        while(curr_class.hasSuperclass()){
          parents.add(m_numberedTypeMap.get(curr_class.getType().toString()));
          curr_class = curr_class.getSuperclass();
        }
      } else if(type instanceof ArrayType){
        SootClass obj_cls = Scene.v().getSootClass("java.lang.Object");
        parents.add(m_numberedTypeMap.get(obj_cls.getType().toString()));
      } else {
        continue;
      }
      m_childrenToParents.put(type.toString(), parents);
    }
    
    m_hierarchyDown = new HashMap<String, List<NumberedType>>();
    SootClass obj_cls = Scene.v().getSootClass("java.lang.Object");
    Type root = obj_cls.getType();
    List<Type> stack = new ArrayList<Type>();
    stack.add(root);
    hierarchyDfs(root, stack);
  }
  
  private void hierarchyDfs(Type curr, List<Type> stack){ 
    List<Type> children = m_parentsToChildren.get(curr.toString());
    if(children == null){
      children = new ArrayList<Type>(); 
    }
    for(Type child : children){
      stack.add(child);
      hierarchyDfs(child, stack);
      stack.remove(stack.size()-1);
    }
    for(int i = 0; i < stack.size(); ++i){
      Type stack_value = stack.get(i);
      List<NumberedType> curr_parents = null;
      if(m_hierarchyDown.containsKey(stack_value.toString())){
        curr_parents = m_hierarchyDown.get(stack_value.toString());
      } else {
        curr_parents = new ArrayList<NumberedType>();
        m_hierarchyDown.put(stack_value.toString(), curr_parents);
      }
      for(int j = i; j < stack.size(); ++j){
        Type child = stack.get(j); 
        NumberedType ntype = m_numberedTypeMap.get(child.toString());
        if(curr_parents.contains(ntype) == false){
          curr_parents.add(ntype);
        }
      }
    }    
  }
  
  public List<NumberedType> getNumberedTypes(){
    return m_numberedTypes;
  }

  public void print() {
    printSet("methods: ", m_dfsMethods);
    System.out.println("parentsToChildren: ");
    for(String parent : m_parentsToChildren.keySet()){
      List<Type> children = m_parentsToChildren.get(parent);
      System.out.println("  "+parent);
      for(Type child : children){
        System.out.println("    "+child);
      }
    }
  }

  private void printSet(String name, Set<String> curr_set) {
    System.out.println(name);
    for(String curr : curr_set){
      System.out.println("  "+curr);
    }
  }

  public List<String> getForwardReachables() {
    List<String> ret = new ArrayList<String>();
    ret.addAll(m_dfsMethods);
    return ret;
  }

  public void addMethod(String signature) {
    if(m_dfsMethods.contains(signature) == false){
      m_dfsMethods.add(signature);
    }
  }

  public boolean containsMethod(String signature) {
    return m_dfsMethods.contains(signature);
  }

  public boolean containsType(Type name) {
    return m_dfsTypes.contains(name);
  }

  public void addType(Type name) {
    if(m_dfsTypes.contains(name) == false){
      m_dfsTypes.add(name);
    }
  }
  
  public void addField(SootField field){
    if(m_dfsFields.contains(field) == false){
      m_dfsFields.add(field);
    }
  }

  public void addSuperClass(Type curr, Type superclass) {
    if(m_parentsToChildren.containsKey(superclass.toString())){
      List<Type> children = m_parentsToChildren.get(superclass.toString());
      if(children.contains(curr) == false){
        children.add(curr);
      }
    } else {
      List<Type> children = new ArrayList<Type>();
      children.add(curr);
      m_parentsToChildren.put(superclass.toString(), children);
    }
  }

  public List<Type> getOrderedTypes() {
    return m_orderedTypes;
  }

  public List<RefType> getOrderedRefTypes() {
    return m_orderedRefTypes;
  }

  public Set<String> getMethods() {
    return m_dfsMethods;
  }

  public Set<SootField> getFields() {
    return m_dfsFields;
  }

  public Set<ArrayType> getArrayTypes() {
    return m_arrayTypes;
  }

  public List<Type> getHierarchy(SootClass input_class) {
    List<NumberedType> nret = m_childrenToParents.get(input_class.getType().toString());
    List<Type> ret = new ArrayList<Type>();
    for(NumberedType ntype : nret){
      try {
      ret.add(ntype.getType());
      } catch(Exception ex){
        ex.printStackTrace();
      }
    }
    return ret;
  }

  private void addBuiltInTypes() {
    addRefType("java.lang.Object");                                 //type 0
    addRefType("java.lang.String");                                 //type 1
    addRefType("java.lang.AbstractStringBuilder");                  //type 2
    addRefType("java.lang.StringBuilder");                          //type 3
    addRefType("java.lang.StackTraceElement");                      //type 4
    addRefType("java.lang.Throwable");                              //type 5
    addRefType("java.lang.Exception");                              //type 6
    addRefType("java.lang.RuntimeException");                       //type 7
    addRefType("java.lang.NullPointerException");                   //type 8. this maps to 8 in edu.syr.pcpratts.rootbeer.Contants.NullPointerNumber
    addRefType("java.lang.Error");                                  //type 9.
    addRefType("java.lang.VirtualMachineError");                    //type 10.
    addRefType("java.lang.OutOfMemoryError");                       //type 11. this maps to 11 in edu.syr.pcpratts.rootbeer.Contants.OutOfMemoryNumber
    addRefType("edu.syr.pcpratts.rootbeer.runtime.RootbeerGpu");
    m_builtInTypes.add(ByteType.v());
    m_builtInTypes.add(CharType.v());
    m_builtInTypes.add(ShortType.v());
    m_builtInTypes.add(IntType.v());
    m_builtInTypes.add(LongType.v());
    m_builtInTypes.add(FloatType.v());
    m_builtInTypes.add(DoubleType.v());
    m_builtInTypes.add(BooleanType.v());
  }

  private void addRefType(String class_name) {
    SootClass soot_class = Scene.v().getSootClass(class_name);
    m_builtInTypes.add(soot_class.getType());
  }

  public List<NumberedType> getNumberedHierarchyUp(SootClass sootClass) {
    return m_childrenToParents.get(sootClass.getType().toString());
  }

  public int getClassNumber(SootClass soot_class) {
    return m_classToNumber.get(soot_class);
  }
  
  public int getClassNumber(Type type) {
    try {
    return (int) m_numberedTypeMap.get(type.toString()).getNumber();
    } catch(Exception ex){
      ex.printStackTrace();
      return 0;
    }
  }

  public List<Type> getOrderedRefLikeTypes() {
    return m_orderedRefLikeTypes;
  }

  public List<NumberedType> getNumberedHierarchyDown(SootClass sootClass) {
    return m_hierarchyDown.get(sootClass.getType().toString());
  }

  public void addCallGraphEdge(SootMethod src, Stmt stmt, SootMethod dest) {
    Edge e = new Edge(src, stmt, dest);
    m_callGraph.addEdge(e);
  }

  public int getCallGraphEdges() {
    return m_callGraph.size();
  }

  public Set<Type> getInstanceOfs() {
    return m_instanceOfs;
  }

  public void addInstanceOf(Type type) {
    if(m_instanceOfs.contains(type) == false){
      m_instanceOfs.add(type);
    }
  }
  
  public List<String> getReachableMethodSigs(){
    return m_reachableMethodSigs;
  }
  
  public void addReachableMethodSig(String signature){
    m_reachableMethodSigs.add(signature);
  }
  
  public void removeTypes(List<Type> types) {
    for(Type type : types){
      if(type instanceof RefType){
        RefType ref_type = (RefType) type;
        SootClass soot_class = ref_type.getSootClass();
        for(SootMethod soot_method : soot_class.getMethods()){
          m_dfsMethods.remove(soot_method.getSignature());
          m_reachableMethodSigs.remove(soot_method.getSignature());
        }
      }
      m_dfsTypes.remove(type);
    }
    
    m_parentsToChildren = new HashMap<String, List<Type>>();
    for(Type type : m_dfsTypes){
      if(type instanceof RefType){
        RefType ref_type = (RefType) type;
        SootClass child = ref_type.getSootClass();
        if(child.hasSuperclass()){
          addSuperClass(type, child.getSuperclass().getType());
        }
      } else if(type instanceof ArrayType){
        SootClass obj_class = Scene.v().getSootClass("java.lang.Object");
        addSuperClass(type, obj_class.getType());
      }
    }
    
    orderTypes();
    createClassHierarchy(); 
  }
}

/*
 *    FastWholeProgram.v().loadToBodyLater("<java.lang.String: void <init>(char[])>");
    SootClass string_class = Scene.v().getSootClass("java.lang.String");
    SootMethod ctor_method = string_class.getMethod("void <init>(char[])");
    addMethod(ctor_method);
    
    FastWholeProgram.v().loadToBodyLater("<edu.syr.pcpratts.rootbeer.runtime.RootbeerGpu: int getThreadId()>");
    SootClass rootbeer_gpu_class = Scene.v().getSootClass("edu.syr.pcpratts.rootbeer.runtime.RootbeerGpu");
    SootMethod getThreadId = rootbeer_gpu_class.getMethod("int getThreadId()");
    addMethod(getThreadId);
    
    ArrayType char_array = ArrayType.v(CharType.v(), 1);
    OpenCLArrayType ocl_array = new OpenCLArrayType(char_array);
    m_arrayTypes.add(ocl_array);
    
    SootClass throwable_class = Scene.v().getSootClass("java.lang.Throwable");
    SootMethod getStackTrace = throwable_class.getMethod("java.lang.StackTraceElement[] getStackTrace()");
    addMethod(getStackTrace);
        
    SootClass stack_trace_elem = Scene.v().getSootClass("java.lang.StackTraceElement");
    SootMethod stack_ctor = stack_trace_elem.getMethod("void <init>(java.lang.String,java.lang.String,java.lang.String,int)");
    addMethod(stack_ctor);
    
    SootClass out_of_mem = Scene.v().getSootClass("java.lang.OutOfMemoryError");
    SootMethod out_ctor = out_of_mem.getMethod("void <init>()");
    addMethod(out_ctor);
    addType(out_of_mem.getType());
    addType("java.lang.VirtualMachineError");
    addType("java.lang.Error");
 */