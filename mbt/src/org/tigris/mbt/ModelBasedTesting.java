//This file is part of the Model-based Testing java package
//Copyright (C) 2005  Kristian Karl
//
//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

package org.tigris.mbt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.tigris.mbt.conditions.CombinationalCondition;
import org.tigris.mbt.conditions.EdgeCoverage;
import org.tigris.mbt.conditions.ReachedEdge;
import org.tigris.mbt.conditions.ReachedState;
import org.tigris.mbt.conditions.StateCoverage;
import org.tigris.mbt.conditions.StopCondition;
import org.tigris.mbt.conditions.TestCaseLength;
import org.tigris.mbt.conditions.TimeDuration;
import org.tigris.mbt.generators.PathGenerator;
import org.tigris.mbt.generators.RandomPathGenerator;
import org.tigris.mbt.generators.ShortestPathGenerator;

import edu.uci.ics.jung.graph.impl.SparseGraph;

/**
 * The object handles the test case generation, both online and offline.
 */
public class ModelBasedTesting
{
	static Logger logger = Logger.getLogger( ModelBasedTesting.class );

	private AbstractModelHandler modelHandler;
	private FiniteStateMachine machine;
	private StopCondition condition;
	private PathGenerator generator;
	private String templateFile;

	public ModelBasedTesting()
	{
		if ( new File( "mbt.properties" ).exists() )
		{
			PropertyConfigurator.configure("mbt.properties");
		}
		else
		{
			SimpleLayout layout = new SimpleLayout();
			WriterAppender writerAppender = null;
	 		try
	 		{
	 			FileOutputStream fileOutputStream = new FileOutputStream( "mbt.log" );
	 			writerAppender = new WriterAppender( layout, fileOutputStream );
	 		} 
	 		catch ( Exception e )
	 		{
				e.printStackTrace();
	 		}
	 
	 		logger.addAppender( writerAppender );
	 		logger.setLevel( (Level)Level.ERROR );
		}
	}

//	private void invokeMethod( String method, boolean dryRun, boolean suppressPrintout ) throws GoBackToPreviousVertexException
//	{
//		Class cls = null;
//		
//		if ( dryRun == false )
//		{
//			cls = _object.getClass();
//			_pathHistory.add( method );
//		}
//
//		if ( method == null )
//		{
//			_pathHistory.add( "" );
//			return;
//		}
//
//
//		try
//		{
//			if ( method.compareTo( "" ) != 0 )
//			{
//				if ( dryRun )
//				{
//					if ( suppressPrintout == false )
//					{
//						System.out.println( method );
//					}
//				}
//				else
//				{
//					Method meth = cls.getMethod( method, null );
//					meth.invoke( _object, null  );
//				}
//			}
//		}
//		catch( NoSuchMethodException e )
//		{
//			logger.error( e );
//			logger.error( "Try to invoke method: " + method );
//			throw new RuntimeException( "The method is not defined: " + method );
//		}
//		catch( java.lang.reflect.InvocationTargetException e )
//		{
//			if ( e.getTargetException().getClass() == GoBackToPreviousVertexException.class )
//			{
//				throw new GoBackToPreviousVertexException();
//			}
//
//			logger.error( e.getCause().getMessage() );
//			e.getCause().printStackTrace();
//			throw new RuntimeException( e.getCause().getMessage() );
//		}
//		catch( Exception e )
//		{
//			logger.error( e );
//			e.printStackTrace();
//			throw new RuntimeException( "Abrupt end of execution: " + e.getMessage() );
//		}
//	}


	public void addCondition(int conditionType, String conditionValue) 
	{
		StopCondition condition = null;
		switch (conditionType) {
		case Keywords.CONDITION_EDGE_COVERAGE:
			condition = new EdgeCoverage(getMachine(), Double.parseDouble(conditionValue)/100);
			break;
		case Keywords.CONDITION_REACHED_EDGE:
			condition = new ReachedEdge(getMachine(), conditionValue);
			break;
		case Keywords.CONDITION_REACHED_STATE:
			condition = new ReachedState(getMachine(), conditionValue);
			break;
		case Keywords.CONDITION_STATE_COVERAGE:
			condition = new StateCoverage(getMachine(), Double.parseDouble(conditionValue)/100);
			break;
		case Keywords.CONDITION_TEST_DURATION:
			condition = new TimeDuration(Long.parseLong(conditionValue));
			break;
		case Keywords.CONDITION_TEST_LENGTH:
			condition = new TestCaseLength(getMachine(), Integer.parseInt(conditionValue));
			break;
		}
		
		Util.AbortIf(condition == null , "Unsupported stop condition selected: "+ conditionType);
		
		if(	this.condition == null )
		{
			this.condition = condition;
		}
		else
		{
			if(!(this.condition instanceof CombinationalCondition))
			{
				StopCondition old= this.condition;
				this.condition = new CombinationalCondition();
				((CombinationalCondition)this.condition).add(old);
			}
			((CombinationalCondition)this.condition).add(condition);
		}
	}

	private StopCondition getCondition()
	{
		return this.condition;
	}

	private FiniteStateMachine getMachine() 
	{
		return this.machine;
	}

	private void setMachine(FiniteStateMachine machine) 
	{
		this.machine = machine;
	}

	/**
	 * Return the instance of the graph
	 */
	public SparseGraph getGraph() {
		return this.modelHandler.getModel();
	}

	public void enableExtended(boolean extended) 
	{
		if(extended)
		{
			setMachine( new ExtendedFiniteStateMachine(getGraph()) );
		}
		else
		{
			setMachine( new FiniteStateMachine(getGraph()) );
		}
	}

	public void setGenerator(int generatorType) {
		switch (generatorType) {
		case Keywords.GENERATOR_RANDOM:
			this.generator = new RandomPathGenerator(getMachine(), getCondition() );
			break;
		case Keywords.GENERATOR_SHORTEST:
			this.generator = new ShortestPathGenerator(getMachine(), getCondition());
			break;
		case Keywords.GENERATOR_STUB:
			try {
				Util.generateCodeByTemplate(getGraph(), this.templateFile);
			} catch (IOException e) {
				logger.fatal("Stub file generation error", e);
				System.exit(-1);
			}
			break;
		case Keywords.GENERATOR_LIST:
			this.generator = null;
			break;
		}
		Util.AbortIf(this.generator == null, "Not implemented yet!");
	}
	
	private PathGenerator getGenerator()
	{
		return this.generator;
	}

	public boolean hasNextStep() {
		Util.AbortIf(getGenerator() == null, "No generator has been defined!");
		return getGenerator().hasNext();
	}

	public String[] getNextStep() {
		Util.AbortIf(getGenerator() == null, "No generator has been defined!");
		try
		{
			return getGenerator().getNext();
		}
		catch(RuntimeException e)
		{
			logger.fatal(e.getMessage());
			System.exit(-1);
		}
		return null;
	}

	public void backtrack() {
		getMachine().backtrack();
	}

	public void readGraph( String graphmlFileName )
	{
		if(this.modelHandler == null)
		{
			this.modelHandler = new GraphML(); 
		}
		this.modelHandler.load( graphmlFileName );
	}

	public void writeModel(String value) {
		this.modelHandler.save(value);
	}

	public void enableBacktrack(boolean backtracking) {
		getMachine().enableBacktrack(backtracking);
	}

	public String getStatisticsString()
	{
		return getMachine().getStatisticsString();
	}
	
	public String getStatisticsCompact()
	{
		return getMachine().getStatisticsStringCompact();
	}

	public String getStatisticsVerbose()
	{
		return getMachine().getStatisticsVerbose();
	}

	public void setTemplate(String templateFile) {
	}
}
