package plugin1.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.corext.util.CollectionsUtil;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SampleHandler extends AbstractHandler {
	public enum Type {
		INT, FLOAT, LONG, DOUBLE, CHAR, STRING, BOOLEAN
	};

	Map<String, Type> assignableFields = new HashMap<String, SampleHandler.Type>();

	/**
	 * The constructor.
	 */
	public SampleHandler() {
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.commands.AbstractHandler#execute(org.eclipse.core.commands
	 * .ExecutionEvent)
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil
				.getActiveWorkbenchWindowChecked(event);

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();

		IProject[] projects = root.getProjects();
		List<IJavaProject> javaProjects = new ArrayList<IJavaProject>();
		// Loop over all projects
		for (IProject project : projects) {
			try {
				if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {

					IJavaProject javaProject = JavaCore.create(project);
					javaProjects.add(javaProject);
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		IJavaProject[] array = javaProjects.toArray(new IJavaProject[] {});
		// IJavaElement[] elements=new IJavaElement[]{Collections};;
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(array);
		FilteredTypesSelectionDialog dialog = new FilteredTypesSelectionDialog(
				window.getShell(), true, null, scope, IJavaSearchConstants.TYPE);
		// FilteredItemsSelectionDialog dialog = new
		// FilteredTypesSelectionDialog(
		// window.getShell(), true, root, IResource.FILE);
		dialog.setTitle("Select Model Class");
		dialog.setInitialPattern("?");
		int open = dialog.open();

		Object[] selectedFiles = dialog.getResult();

		for (Object file : selectedFiles) {
			generateBuilder((org.eclipse.jdt.internal.core.SourceType) file);
		}
		// org.eclipse.core.internal.resources.File selectedFile =
		// (org.eclipse.core.internal.resources.File) result[0];
		//
		// generateBuilder(selectedFile);

		return null;
	}

	private void generateBuilder(
			org.eclipse.jdt.internal.core.SourceType selectedFile) {
		// IProject project = selectedFile.getJavaProject()

		IJavaProject javaProject = selectedFile.getJavaProject();

		String pathOfSelectedFile = selectedFile.getParent().getPath()
				.removeLastSegments(1).toString();
	
		// packageFragmentRootSrc represents the package root of the source file for which we want to generate builder
		IPackageFragmentRoot packageFragmentRootSrc = null;
		try {
			for (IPackageFragmentRoot packageRoot : javaProject
					.getPackageFragmentRoots()) {
				if (pathOfSelectedFile.startsWith(packageRoot.getPath()
						.toString())) {
					packageFragmentRootSrc = packageRoot;
					break;
				}
			}
		} catch (JavaModelException e2) {
			e2.printStackTrace();
		}
		String pathOfDestinationFile=null;
		
		if(pathOfSelectedFile.contains("/src/main/")){
			/*for maven based project the path of source(Model) and destination(Builder) are different*/
			pathOfDestinationFile=pathOfSelectedFile.replaceFirst("/src/main/", "/src/test/");
		}else{
			
			/*for non-maven based project the path of source and destination are same*/
			pathOfDestinationFile=pathOfSelectedFile;
		}
		// packageFragmentRootDest represents the package root of the builder file 
		IPackageFragmentRoot packageFragmentRootDest = null;
		try {
			for (IPackageFragmentRoot packageRoot : javaProject
					.getPackageFragmentRoots()) {
				if (pathOfDestinationFile.startsWith(packageRoot.getPath()
						.toString())) {
					packageFragmentRootDest = packageRoot;
					break;
				}
			}
		} catch (JavaModelException e2) {
			e2.printStackTrace();
		}
		
		String fullPackage = pathOfSelectedFile.replaceFirst(
				packageFragmentRootSrc.getPath().toString(), "");
		// remove first / and replace / with .
		if (fullPackage.length() > 0) {
			fullPackage = fullPackage.substring(1);
		}
		fullPackage = fullPackage.replaceAll("/", ".");
		IPackageFragment packageFragmentSrc = packageFragmentRootSrc
				.getPackageFragment(fullPackage);
		
		IPackageFragment packageFragmentDest = packageFragmentRootDest
				.getPackageFragment(fullPackage);
		if(!packageFragmentDest.exists()){
			try {
				packageFragmentDest = packageFragmentRootDest
						.createPackageFragment(fullPackage, true, null);
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
			
		}

		// String name = selectedFile.getTypeQualifiedName();
		String nameWithoutExtension = selectedFile.getTypeQualifiedName();
		String builderClass = nameWithoutExtension + "Builder";
		String builderFile = builderClass + ".java";

		ICompilationUnit bcu = null;

		IType type = null;
		try {
			StringBuilder builderClassStringBuilder = new StringBuilder();
			builderClassStringBuilder.append("public class ");
			builderClassStringBuilder.append(builderClass);
			builderClassStringBuilder.append("{}");
			bcu = packageFragmentDest.createCompilationUnit(builderFile,
					builderClassStringBuilder.toString(), true, null);

			if (fullPackage != null && !fullPackage.equals("")) {
				bcu.createPackageDeclaration(fullPackage, null);
			}
			type = bcu.getType(builderClass);
			StringBuilder fieldStringBuilder = new StringBuilder();
			fieldStringBuilder.append("private ");
			fieldStringBuilder.append(nameWithoutExtension);
			fieldStringBuilder.append(" ");
			fieldStringBuilder.append(reverseCapitalize(nameWithoutExtension));
			fieldStringBuilder.append("= new ");
			fieldStringBuilder.append(nameWithoutExtension);
			fieldStringBuilder.append("();");
			type.createField(fieldStringBuilder.toString(), null, true, null);

			StringBuilder newInstanceStringBuilder = new StringBuilder();
			newInstanceStringBuilder.append("public static ");
			newInstanceStringBuilder.append(builderClass);
			newInstanceStringBuilder.append(" newInstance(){return new ");
			newInstanceStringBuilder.append(builderClass);
			newInstanceStringBuilder.append("();}");
			type.createMethod(newInstanceStringBuilder.toString(),
					null, true, null);
		} catch (JavaModelException e2) {
			e2.printStackTrace();
		}
		ICompilationUnit cu = packageFragmentSrc
				.getCompilationUnit(nameWithoutExtension + ".java");
	//	IType modelClassType = cu.getType(nameWithoutExtension);

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu); // set source
		parser.setResolveBindings(true); // we need bindings later on
		final CompilationUnit compilationUnit = (CompilationUnit) parser
				.createAST(null /* IProgressMonitor */);

		List<AbstractTypeDeclaration> types = compilationUnit.types();
		for (AbstractTypeDeclaration type2 : types) {
			if (type2.getNodeType() == ASTNode.TYPE_DECLARATION) {
				// Class def found
				List<BodyDeclaration> bodies = type2.bodyDeclarations();
				for (BodyDeclaration body : bodies) {
					if (body.getNodeType() == ASTNode.FIELD_DECLARATION) {
						FieldDeclaration field = (FieldDeclaration) body;
						if(listContainsModifier(field.modifiers(),Modifier.STATIC)){
							//skip static fields
							continue;
						}
						Object o = field.fragments().get(0);
						String fieldName = ((VariableDeclarationFragment) o)
								.getName().toString();
						String completeFieldtype = field.getType()
								.resolveBinding().getQualifiedName();
						String fieldType = field.getType().toString();
						String fieldPackage = null;
						if (!(field.getType() instanceof PrimitiveType)) {

							fieldPackage = field.getType().resolveBinding()
									.getPackage().getName();
							if (completeFieldtype.contains("<")) {
								// to avoid import like import
								// java.util.List<java.lang.String>
								String[] importArray = completeFieldtype
										.split("<");
								completeFieldtype = importArray[0];
								String classToImport = importArray[1]
										.substring(0,
												importArray[1].length() - 1);
								if (classToImport.contains(",")) {
									// import two generic classes of map
									String[] clazz = classToImport.split(",");
									importGenericClasses(bcu, clazz[0]);
									importGenericClasses(bcu, clazz[1]);
								} else {
									// import one generic classes of collection
									importGenericClasses(bcu, classToImport);
								}

							} else {
								// import base class
								importGenericClasses(bcu, completeFieldtype);
								if (completeFieldtype
										.equals("java.lang.String")) {
									assignableFields
											.put(fieldName, Type.STRING);
								} else if (completeFieldtype
										.equals("java.lang.Integer")) {
									assignableFields.put(fieldName, Type.INT);
								} else if (completeFieldtype
										.equals("java.lang.Long")) {
									assignableFields.put(fieldName, Type.LONG);
								} else if (completeFieldtype
										.equals("java.lang.Double")) {
									assignableFields
											.put(fieldName, Type.DOUBLE);
								} else if (completeFieldtype
										.equals("java.lang.Float")) {
									assignableFields.put(fieldName, Type.FLOAT);
								} else if (completeFieldtype
										.equals("java.lang.Character")) {
									assignableFields.put(fieldName, Type.CHAR);
								} else if (completeFieldtype
										.equals("java.lang.Boolean")) {
									assignableFields.put(fieldName,
											Type.BOOLEAN);
								}
							}

							if (fieldType.contains("<")) {
								String string = fieldType.split("<")[1];
								fieldType = string.substring(0,
										string.length() - 1);
							}

						}else{
							
							String primitiveType = field.getType().toString();
							
							if(primitiveType.equals(PrimitiveType.INT.toString())){
								assignableFields.put(fieldName, Type.INT);
							}else if (primitiveType.equals(PrimitiveType.LONG.toString())) {
								assignableFields.put(fieldName, Type.LONG);
							} else if (primitiveType.equals(PrimitiveType.DOUBLE.toString())) {
								assignableFields
										.put(fieldName, Type.DOUBLE);
							} else if (primitiveType.equals(PrimitiveType.FLOAT.toString())) {
								assignableFields.put(fieldName, Type.FLOAT);
							} else if (primitiveType.equals(PrimitiveType.CHAR.toString())) {
								assignableFields.put(fieldName, Type.CHAR);
							} else if (primitiveType.equals(PrimitiveType.BOOLEAN.toString())) {
								assignableFields.put(fieldName,
										Type.BOOLEAN);
							}
							
							
							
						}
						
						String methodBody = "";
						Class clazz = null;
						try {
							clazz = Class.forName(completeFieldtype);
						} catch (ClassNotFoundException e1) {
							e1.printStackTrace();
						}
						if (fieldPackage != null
								&& fieldPackage.startsWith("java.util")
								&& Collection.class.isAssignableFrom(clazz)) {
							StringBuilder stringBuilder = new StringBuilder();
									stringBuilder.append(reverseCapitalize(nameWithoutExtension));
									stringBuilder.append(".get");
									stringBuilder.append(capitalize(fieldName));
									stringBuilder.append("().add(");
									stringBuilder.append(reverseCapitalize(fieldName));
									stringBuilder.append(");");
							methodBody = stringBuilder.toString();
						} else if (fieldPackage != null
								&& fieldPackage.startsWith("java.util")
								&& Map.class.isAssignableFrom(clazz)) {
							StringBuilder stringBuilder = new StringBuilder();
									stringBuilder.append(reverseCapitalize(nameWithoutExtension));
									stringBuilder.append(".get");
									stringBuilder.append(capitalize(fieldName));
									stringBuilder.append("().put(key,value);");
							methodBody = stringBuilder.toString();
						} else {
							StringBuilder stringBuilder = new StringBuilder();
							stringBuilder.append(reverseCapitalize(nameWithoutExtension));
							stringBuilder.append(".set");
							stringBuilder.append(capitalize(fieldName));
							stringBuilder.append("(");
							stringBuilder.append(reverseCapitalize(fieldName));
							stringBuilder.append(");");
							methodBody = stringBuilder.toString();
						}

						try {
							if (fieldType.contains(",")) {
								// for Map and sub classes
								String[] fieldTypes = fieldType.split(",");
								StringBuilder stringBuilder = new StringBuilder();
								stringBuilder.append("public ");
								stringBuilder.append(builderClass);
								stringBuilder.append(" ");
								stringBuilder.append(fieldName);
								stringBuilder.append("(");
								stringBuilder.append(fieldTypes[0]);
								stringBuilder.append(" key,");
								stringBuilder.append(fieldTypes[1]);
								stringBuilder.append(" value){");
								stringBuilder.append(methodBody);
								stringBuilder.append("return this;");
								stringBuilder.append("}");
								type.createMethod(stringBuilder.toString(),
										null, true, null);
							} else {
								StringBuilder stringBuilder = new StringBuilder();
								stringBuilder.append("public ");
								stringBuilder.append(builderClass);
								stringBuilder.append(" ");
								stringBuilder.append(fieldName);
								stringBuilder.append("(");
								stringBuilder.append(fieldType);
								stringBuilder.append(" ");
								stringBuilder.append(reverseCapitalize(fieldName));
								stringBuilder.append("){");
								stringBuilder.append(methodBody);
								stringBuilder.append("return this;");
								stringBuilder.append("}");
								type.createMethod(stringBuilder.toString(), null, true, null);
							}
						} catch (JavaModelException e) {
							e.printStackTrace();
						}

					}
				}

			}
		}

		try {
			StringBuilder sb = new StringBuilder();
			sb.append("public ");
			sb.append(nameWithoutExtension);
			sb.append(" build(){return this.");
			sb.append(reverseCapitalize(nameWithoutExtension));
			sb.append(";}");
			type.createMethod(sb.toString(), null,
					true, null);

			StringBuilder sb2 = new StringBuilder();
			sb2.append("public static ");
			sb2.append(nameWithoutExtension);
			sb2.append(" any");
			sb2.append(nameWithoutExtension);
			sb2.append("(){\n//TODO\n");
			sb2.append("return newInstance()");
			sb2.append(generateFactoryMethodBody());
			sb2.append(".build();}");
			type.createMethod(sb2.toString(), null, true, null);
		} catch (JavaModelException e1) {
			e1.printStackTrace();
		}

		try {
			CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(null);
			String source = bcu.getSource();
			TextEdit textEdit = codeFormatter.format(
					CodeFormatter.K_COMPILATION_UNIT, source, 0,
					source.length(), 0, null);
			bcu.applyTextEdit(textEdit, null);
			bcu.save(null, true);
			JavaUI.openInEditor(bcu);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void importGenericClasses(ICompilationUnit bcu, String classToImport) {
		if (classToImport.contains(".")) {
			try {
				bcu.createImport(classToImport, null, null);
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		}
	}

	public String capitalize(String s) {
		return s.replaceFirst(String.valueOf(s.charAt(0)),
				String.valueOf(s.charAt(0)).toUpperCase());
	}

	public String reverseCapitalize(String s) {
		return s.replaceFirst(String.valueOf(s.charAt(0)),
				String.valueOf(s.charAt(0)).toLowerCase());
	}

	private String generateFactoryMethodBody() {
		StringBuilder factoryMethodBody = new StringBuilder();
		Set<String> keySet = assignableFields.keySet();
		for (String key : keySet) {
			if (assignableFields.get(key) == Type.STRING) {
				factoryMethodBody.append(".").append(key).append("(\"")
						.append(key).append("\")");
			} else if (assignableFields.get(key) == Type.INT) {
				factoryMethodBody.append(".").append(key).append("(")
						.append( (int)(Math.random()*10)).append(")");
			} else if (assignableFields.get(key) == Type.LONG) {
				factoryMethodBody.append(".").append(key).append("(")
						.append((int)(Math.random()*10)).append("l)");
			} else if (assignableFields.get(key) == Type.FLOAT) {
				factoryMethodBody.append(".").append(key).append("(")
						.append((int)(Math.random()*10)+.5).append("f)");
			} else if (assignableFields.get(key) == Type.DOUBLE) {
				factoryMethodBody.append(".").append(key).append("(")
						.append((int)(Math.random()*10)+.5).append(")");
			} else if (assignableFields.get(key) == Type.CHAR) {
				factoryMethodBody.append(".").append(key).append("('")
						.append('a').append("')");
			} else if (assignableFields.get(key) == Type.BOOLEAN) {
				factoryMethodBody.append(".").append(key).append("(true")
						.append(")");
			} 
		}

		return factoryMethodBody.toString();
	}
	public static boolean  listContainsModifier(List<Modifier> modifierList,int mod){
		boolean contains=false;
		for (Modifier modifier : modifierList) {
			if(modifier.getKeyword().toFlagValue()==mod){
				contains=true;
			}
		}
		return contains;
	}
}
