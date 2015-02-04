package plugin1.handlers;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
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
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SampleHandler extends AbstractHandler {
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
		FilteredItemsSelectionDialog dialog = new FilteredResourcesSelectionDialog(
				window.getShell(), true, root, IResource.FILE);
		dialog.setTitle("Select Model Class");
		dialog.setInitialPattern("?");
		int open = dialog.open();

		Object[] selectedFiles = dialog.getResult();
		
		
		
		
		for (Object file : selectedFiles) {
			generateBuilder((org.eclipse.core.internal.resources.File) file);
		}
		// org.eclipse.core.internal.resources.File selectedFile =
		// (org.eclipse.core.internal.resources.File) result[0];
		//
		// generateBuilder(selectedFile);

		return null;
	}

	private void generateBuilder(
			org.eclipse.core.internal.resources.File selectedFile) {
		IProject project = selectedFile.getProject();

		IJavaProject javaProject = JavaCore.create(project);

		String pathOfSelectedFile = selectedFile.getParent()
				.getProjectRelativePath().toString();
		IPackageFragmentRoot packageFragmentRoot = null;
		try {
			for (IPackageFragmentRoot packageRoot : javaProject
					.getPackageFragmentRoots()) {
				if (pathOfSelectedFile.startsWith(packageRoot.getPath()
						.removeFirstSegments(1).toString())) {
					packageFragmentRoot = packageRoot;
					break;
				}
			}
		} catch (JavaModelException e2) {
			e2.printStackTrace();
		}
		String fullPackage = pathOfSelectedFile
				.replaceFirst(packageFragmentRoot.getPath()
						.removeFirstSegments(1).toString(), "");
		// remove first / and replace / with .
		if (fullPackage.length() > 0) {
			fullPackage = fullPackage.substring(1);
		}
		fullPackage = fullPackage.replaceAll("/", ".");
		IPackageFragment packageFragment = packageFragmentRoot
				.getPackageFragment(fullPackage);

		String name = selectedFile.getName();
		String nameWithoutExtension = name.substring(0, name.indexOf("."));
		String builderClass = nameWithoutExtension + "Builder";
		String builderFile = builderClass + ".java";

		ICompilationUnit bcu = null;

		IType type = null;
		try {
			bcu = packageFragment.createCompilationUnit(builderFile,
					"public class " + builderClass + "{}", true, null);

			if (fullPackage != null && !fullPackage.equals("")) {
				bcu.createPackageDeclaration(fullPackage, null);
			}
			type = bcu.getType(builderClass);
			type.createField("private " + nameWithoutExtension + " "
					+ reverseCapitalize(nameWithoutExtension) + "= new "
					+ nameWithoutExtension + "();", null, true, null);

			type.createMethod("public static " + builderClass
					+ " newInstance(){return new " + builderClass + "();}",
					null, true, null);
		} catch (JavaModelException e2) {
			e2.printStackTrace();
		}
		ICompilationUnit cu = packageFragment.getCompilationUnit(name);
		IType modelClassType = cu.getType(nameWithoutExtension);

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
								if (classToImport.contains(".")) {
									try {
										bcu.createImport(classToImport, null,
												null);
									} catch (JavaModelException e) {
										e.printStackTrace();
									}
								}
							} else {
								if (completeFieldtype.contains(".")) {
									try {
										bcu.createImport(completeFieldtype,
												null, null);
									} catch (JavaModelException e) {
										e.printStackTrace();
									}
								}
							}

							if (fieldType.contains("<")) {
								String string = fieldType.split("<")[1];
								fieldType = string.substring(0,
										string.length() - 1);
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
							methodBody = reverseCapitalize(nameWithoutExtension)
									+ ".get" + capitalize(fieldName)
									+ "().add(" + reverseCapitalize(fieldName)
									+ ");";
						} else {
							methodBody = reverseCapitalize(nameWithoutExtension)
									+ ".set" + capitalize(fieldName) + "("
									+ reverseCapitalize(fieldName) + ");";
						}

						try {
							type.createMethod("public " + builderClass + " "
									+ fieldName + "(" + fieldType + " "
									+ reverseCapitalize(fieldName) + "){"
									+ methodBody + "return this;" + "}", null,
									true, null);
						} catch (JavaModelException e) {
							e.printStackTrace();
						}

					}
				}

			}
		}

		try {
			type.createMethod(
					"public " + nameWithoutExtension + " build(){return this."
							+ reverseCapitalize(nameWithoutExtension) + ";}", null,
					true, null);

			type.createMethod("public static " + nameWithoutExtension + " any"
					+ nameWithoutExtension + "(){\n//TODO\n"
					+ "return newInstance().build();}", null, true, null);
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

	public String capitalize(String s) {
		return s.replaceFirst(String.valueOf(s.charAt(0)),
				String.valueOf(s.charAt(0)).toUpperCase());
	}
	
	public String reverseCapitalize(String s) {
		return s.replaceFirst(String.valueOf(s.charAt(0)),
				String.valueOf(s.charAt(0)).toLowerCase());
	}
}
