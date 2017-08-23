/*
 * 	 This file is part of Bedspread, originally promoted and
 *	 developed at CNR-IASI. For more information visit:
 *	 http://leks.iasi.cnr.it/tools/bedspread
 *	     
 *	 This is free software: you can redistribute it and/or modify
 *	 it under the terms of the GNU General Public License as 
 *	 published by the Free Software Foundation, either version 3 of the 
 *	 License, or (at your option) any later version.
 *	 
 *	 This software is distributed in the hope that it will be useful,
 *	 but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	 GNU General Public License for more details.
 * 
 *	 You should have received a copy of the GNU General Public License
 *	 along with this source.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.cnr.iasi.leks.bedspread.rdf.sparqlImpl;

import java.security.SecureRandom;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cnr.iasi.leks.bedspread.config.PropertyUtil;

/**
 * 
 * @author ftaglino
 *
 */
public class SPARQLEndpointConnector {
	private String endpointUrl = "";
	
	private static final long TIME_ELAPSED_THRESHOLD = 10000;
	
	private static final long MAX_SLEEP_TIME = 500;
	
	private static final int DEFAULT_MAX_CONCURRENT_SPARQL_THREAD = 50;

	protected final Logger logger = LoggerFactory.getLogger(SPARQLEndpointConnector.class);
	
	private SecureRandom random;
	
	private static final Object MUTEX = new Object();
	private static volatile Semaphore QUERY_INVOKATION_SEMAPHORE = new Semaphore(PropertyUtil.getInstance().getProperty(PropertyUtil.MAX_CONCURRENT_SPARQL_THREAD_LABEL, DEFAULT_MAX_CONCURRENT_SPARQL_THREAD));
			
	
	public SPARQLEndpointConnector(String endpointUrl) {
		super();
		this.endpointUrl = endpointUrl;
	    this.random = new SecureRandom();
	}

	public String getEndpointUrl() {
		return endpointUrl;
	}

	public void setEndpointUrl(String endpointUrl) {
		this.endpointUrl = endpointUrl;
	}

	/**
	 * Execute a query on the configured Sparql endpoint
	 * @param url The address of the SPARQL endpoint
	 * @param queryString The SPARQL query
	 * @return Vector<QuerySolution> 
	 */	
	public Vector<QuerySolution> execQuery(String queryString) {	
		Vector<QuerySolution> qss = new Vector<QuerySolution>();
		//queryString = queryString.replaceAll("\n", "\\u000D");
		
		try{
			Query query = QueryFactory.create(queryString);
			
//			ResultSet r = this.executeQuery(query);
//			ResultSet r = qexec.execSelect();	
			ResultSet r = this.executeQueryWithSemaphore(query);
			
			while(r.hasNext()){
				qss.add(r.next());
			}
		}catch(Exception ex) {
			this.logger.error("#Query: {}; #Message: {}; #Cause: {}", queryString, ex.getMessage(), ex.getCause());
		}
		
		return qss;
	}
	
	/*
	 * This method has been introduce in order to mitigate 
	 * the simultaneous invocation of the SPARQL endpoint
	 * by that different concurrent threads 
	 */
	private ResultSet executeQuery(Query query) throws Exception{
		ResultSet r = null;

		long ts1 = 0;
		long ts2 = 0;
		
		QueryExecution qexec = null;

		try{
			synchronized (MUTEX) {
				qexec = QueryExecutionFactory.sparqlService(endpointUrl, query);

				ts1 = System.currentTimeMillis();
				r = qexec.execSelect();
				ts2 = System.currentTimeMillis();
				
//				this.waitABit();
			}
		}catch(Exception ex) {
			ts2 = System.currentTimeMillis();
			throw ex;
		}finally {
			if(qexec!=null){
				qexec.close();
			}	
			long delta = ts2 - ts1;
			this.logQueriesWithLongTimeProcessing(delta, query.toString());
		}
		
		return r; 
	}

	/*
	 * This method has been introduce in order to mitigate 
	 * the simultaneous invocation of the SPARQL endpoint
	 * by that different concurrent threads 
	 */
	private ResultSet executeQueryWithSemaphore (Query query) throws Exception {
		ResultSet r = null;

		long ts1 = 0;
		long ts2 = 0;
		
		QueryExecution qexec = null;

		try{
			QUERY_INVOKATION_SEMAPHORE.acquire();
			
			qexec = QueryExecutionFactory.sparqlService(endpointUrl, query);
			
			ts1 = System.currentTimeMillis();
			r = qexec.execSelect();
			ts2 = System.currentTimeMillis();
		}catch(Exception ex) {
			ts2 = System.currentTimeMillis();
			throw ex;
		}finally {
			if(qexec!=null){
				qexec.close();
			}	
			QUERY_INVOKATION_SEMAPHORE.release();			
			long delta = ts2 - ts1;
			this.logQueriesWithLongTimeProcessing(delta, query.toString());
		}		
		
		return r; 
	}

	
	/**
	 * Execute a query on the configured Sparql endpoint
	 * @param url The address of the SPARQL endpoint
	 * @param queryString The SPARQL query
	 * @return Vector<QuerySolution> 
	 */	
	public boolean execAsk(String queryString) {	
		boolean result = false;
		//queryString = queryString.replaceAll("\n", "\\u000D");
				
		try {	
			Query query = QueryFactory.create(queryString);
			
//			result = this.executeAsk(query);			
			result = this.executeAskWithSemaphore(query);			
		}
		catch(Exception ex) {
			this.logger.error("#Query: {}; #Message: {}; #Cause: {}", queryString, ex.getMessage(), ex.getCause());
		}
		return result;
	}
	
	/*
	 * This method has been introduce in order to mitigate 
	 * the simultaneous invocation of the SPARQL endpoint
	 * by that different concurrent threads 
	 */
	private boolean executeAsk(Query query) throws Exception {
		boolean result = false;

		QueryExecution qexec = null;

		long ts1 = 0;
		long ts2 = 0;
		
		try{
			synchronized (MUTEX) {
				qexec = QueryExecutionFactory.sparqlService(endpointUrl, query);

				ts1 = System.currentTimeMillis();
				result = qexec.execAsk();	
				ts2 = System.currentTimeMillis();
			
//				this.waitABit();
			}
		} catch(Exception ex) {
			ts2 = System.currentTimeMillis();
			throw ex;
		} finally {
			if(qexec!=null)
				qexec.close();
			long delta = ts2 - ts1;
			this.logQueriesWithLongTimeProcessing(delta, query.toString());
		}
	

		return result; 
	}

	/*
	 * This method has been introduce in order to mitigate 
	 * the simultaneous invocation of the SPARQL endpoint
	 * by that different concurrent threads 
	 */
	private boolean executeAskWithSemaphore(Query query) throws Exception{
		boolean result = false;

		long ts1 = 0;
		long ts2 = 0;
		
		QueryExecution qexec = null;
		
		try{
			QUERY_INVOKATION_SEMAPHORE.acquire();

			qexec = QueryExecutionFactory.sparqlService(endpointUrl, query);
			
			ts1 = System.currentTimeMillis();
			result = qexec.execAsk();
			ts2 = System.currentTimeMillis();
		}catch(Exception ex) {
			ts2 = System.currentTimeMillis();
			throw ex;
		}finally {
			if(qexec!=null){
				qexec.close();
			}	
			QUERY_INVOKATION_SEMAPHORE.release();			
			long delta = ts2 - ts1;
			this.logQueriesWithLongTimeProcessing(delta, query.toString());
		}
		
		return result; 
	}
	
	private void waitABit() {
		long millis = Math.round(this.random.nextDouble() * MAX_SLEEP_TIME); 
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			this.logger.warn(e.getMessage());
		}
	}

	private void logQueriesWithLongTimeProcessing(long delta, String queryString){
		if (delta >= TIME_ELAPSED_THRESHOLD){
			this.logger.info("Time Elapsed for Query Exec: {} ms, {}", delta, queryString);
		}
	}

}
