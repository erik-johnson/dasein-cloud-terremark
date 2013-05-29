/**
 * Copyright (C) 2009-2013 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.terremark;

import java.util.ArrayList;

import javax.annotation.Nullable;

/**
 * Layout allows organizations to organize their devices (both physical and virtual) into rows and groups so the environment can be surveyed easily from visual and logical perspectives.
 * Each layout has one or more rows. Each row has one or more groups.
 * @author Erik Johnson
 *
 */
public class Layout {

	private ArrayList<Row> rows = new ArrayList<Row>();

	public ArrayList<Row> getRows() {
		return rows;
	}

	public void setRows(ArrayList<Row> rows) {
		this.rows = rows;
	}

	public void addRow(Row row){
		rows.add(row);
	}

	/**
	 * Returns true if the layout contains a row named rowName and that row contains a group named groupName.
	 * @param rowName The name of the row.
	 * @param groupName The name of the group.
	 * @return true if the layout contains a row named rowName and that row contains a group named groupName.
	 */
	public boolean contains(String rowName, String groupName){
		boolean contains = false;
		for (Row row : rows){
			if (row.getName().equals(rowName)){
				if( row.hasGroup(groupName)){
					contains = true;
					break;
				}
			}
		}
		return contains;
	}
	
	/**
	 * Returns the row id of a row named rowName that contains a group named groupName.
	 * @param rowName The name of the row you are looking for.
	 * @param groupName The name of the group you are looking for.
	 * @return the row id of a row named rowName that contains a group named groupName.
	 */
	public @Nullable Row getRowId(String rowName, String groupName){
		Row matchingRow = null;
		for (Row row : rows){
			if (row.getName().equals(rowName)){
				if( row.hasGroup(groupName)){
					matchingRow = row;
					break;
				}
			}
		}
		return matchingRow;
	}
	
	/**
	 * Returns true if the layout contains a row matching rowName
	 * @param rowName The name of the row you are looking for.
	 * @return true if the layout contains a row matching rowName
	 */
	public boolean contains(String rowName){
		boolean contains = false;
		for (Row row : rows){
			if (row.getName().equals(rowName)){
				contains = true;
				break;
			}
		}
		return contains;
	}
	
	/**
	 * Returns the fist row matching rowName
	 * @param rowName
	 * @return the fist row matching rowName
	 */
	public @Nullable Row getRowId(String rowName) {
		Row matchingRow = null;
		for (Row row : rows){
			if (row.getName().equals(rowName)){
				matchingRow = row;
				break;
			}
		}
		return matchingRow;
	}
	
}
