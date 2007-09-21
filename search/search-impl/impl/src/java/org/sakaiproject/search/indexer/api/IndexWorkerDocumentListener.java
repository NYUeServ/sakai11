/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.search.indexer.api;

/**
 * An IndexWorkerDocumentListener is notified as Documents are indexed
 * 
 * @author ieb
 */
public interface IndexWorkerDocumentListener
{

	/**
	 * Fired when a document starts to be indexed
	 * 
	 * @param worker
	 *        the worker performing the index operation
	 * @param ref
	 *        the document being indexed
	 */
	void indexDocumentStart(IndexWorker worker, String ref);

	/**
	 * fired when a document has completed indexing
	 * 
	 * @param worker
	 *        the worker performing the operation
	 * @param ref
	 *        a reference to the document
	 */
	void indexDocumentEnd(IndexWorker worker, String ref);

}
