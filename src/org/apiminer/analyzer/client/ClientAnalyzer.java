package org.apiminer.analyzer.client;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apiminer.analyzer.AnalyzerException;
import org.apiminer.entities.ProjectAnalyserStatistic;
import org.apiminer.entities.api.ApiClass;
import org.apiminer.entities.api.Project;
import org.apiminer.util.FilesUtil;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;


/**
 * @author Hudson S. Borges
 * 
 */
public class ClientAnalyzer {

	/**
	 * default compiling version
	 */
	public static final String DEFAULT_CODE_VERSION = JavaCore.VERSION_1_6;
	
	private final Logger logger = Logger.getLogger(ClientAnalyzer.class);

	private Collection<String> files;
	
	private LinkedList<String> jars;

	private Project api;
	
	private String sourceCodeDirectory;
	
	private List<String> sourceDirectories;

	private Set<ApiClass> apiClasses;
	
	private ProjectAnalyserStatistic statistics;

	private ASTParser parser;
	
	private ClientAnalyzer(String sourceFilesDirectory) throws AnalyzerException {
		this.parser = ASTParser.newParser(AST.JLS3);

		this.files = FilesUtil.collectFiles(sourceFilesDirectory, ".java", true);
		this.jars = new LinkedList<String>(FilesUtil.collectFiles(sourceFilesDirectory, ".jar", true));
		this.sourceDirectories = FilesUtil.collectDirectories(sourceFilesDirectory, "src");
		this.sourceDirectories.add(sourceFilesDirectory);
	}

	public ClientAnalyzer(String sourceFilesDirectory,
			String[] jarsDependencies, Project sourceProject) throws AnalyzerException {
		
		this(sourceFilesDirectory);
		
		for (String jarFile : jarsDependencies) {
			File file = new File(jarFile);
			if (!file.exists() || !file.getName().toLowerCase().endsWith(".jar")) {
				throw new AnalyzerException("The dependencies must be jar files.");
			}
		}
		
		for (int i = jarsDependencies.length - 1; i >= 0; i--) {
			this.jars.addFirst(jarsDependencies[i]);
		}
		this.api = sourceProject;
		this.sourceCodeDirectory = sourceFilesDirectory;
	}

	/**
	 * set the parser options
	 */
	private void setOptions() {
		parser.setEnvironment(
				jars.toArray(new String[0]),
				sourceDirectories.toArray(new String[0]),
				null,
				true);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);

		@SuppressWarnings("unchecked")
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, DEFAULT_CODE_VERSION);
		options.put(
				JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
				DEFAULT_CODE_VERSION);
		options.put(JavaCore.COMPILER_SOURCE, DEFAULT_CODE_VERSION);

		parser.setCompilerOptions(options);
	}

	public void parse() {
		this.setOptions();

		final ClientVisitor visitor = new ClientVisitor(api);

		logger.debug("Analyzing the source files in the directory " + sourceCodeDirectory);
		
		FileASTRequestor requestor = new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit ast) {
				logger.debug("Analyzing the source file: " + sourceFilePath);
				ast.getRoot().accept(visitor);
			}
		};

		parser.createASTs(
				files.toArray(new String[0]),
				null,
				new String[0],
				requestor,
				null);
		
		System.gc();

		logger.debug("The source files in the directory " + sourceCodeDirectory + " were analyzed.");
		
		this.apiClasses = visitor.getApiClasses();
		this.statistics = visitor.getStatistics();
	}

	public Set<ApiClass> getApiClasses() {
		return apiClasses;
	}

	public ProjectAnalyserStatistic getStatistics() {
		return statistics;
	}

	public Collection<String> getJarsDependency() {
		return jars;
	}

}
