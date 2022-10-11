# DEV-DiagramChannel-Writer

__Channel Export__ - Thu May 05 2022 15:04:35 GMT-0000 (UTC)

__Destination Scripts__
```
This channel has 6 destinations.



________________________________________________________________________________________________________________________________
Destination 1 Output Diagram SVG in HTML



____________________________________________________________
Transformer



____________________________________________________________
Step 0 Output Filename		

// @apiinfo """Converts the input filename *.xml to *.html and stores the result in ${xformoutputfilename}"""
var name = String(sourceMap.get('originalFilename'));
var output = name.split('.xml')[0] + ".html";
channelMap.put('xformoutputfilename', output);

____________________________________________________________
Step 1 CSS stylesheet		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- @apiinfo """262 lines. Controls all styles for the HTML and SVG output of the Transformer.""" -->

	<xsl:template match="/">
		<style><![CDATA[
            /* base styles */
            body {
                margin-left: 15px;
                font-family: Arial;
            }
            h2 {
                padding-left: 8px;
            }
            p {
                margin-left: 10px;
            }
            table.notes {
                margin-left: 10px;
                border-collapse: collapse;
            }
            table.notes th {
                text-align: left;
            }
            table.notes td.raw {
                background-color: #f0f0f0;
            }
            /* active colors
                (red) #CE1141; Atlanta Braves
                (blue) #004687; KC Royals
                (green) #005C5C; Seattle Mariners
                (gold) #FFC72C; Warriors
            */

            /* background panels */

            rect.srcbg {
                fill: #e6e6e6;
                stroke: #e6e6e6;
            }
            rect.dstbg {
                fill: #f0f0f0;
                stroke: #f0f0f0;
            }
            rect.xformpanel {
                fill: white;
                stroke: white;
            }
            text.bgbig {
                font-size: 0.7em;
                text-anchor: left;
                fill: #090909;
                font-weight: bold;
            }

            /* dimmed deploy/undeploy */

            rect.deployoff {
                fill: white;
                stroke: silver;
            }
            text.deployoff {
                font-size: 0.55em;
                text-anchor: middle;
                fill: darkgray;
                font-weight: normal;
            }

            /* dimmed data in */

            rect.dataoff {
                fill: white;
                stroke: silver;
            }
            line.dataoff {
                stroke-width: 3;
                stroke: silver;
            }
            polygon.dataoff {
                fill: silver;
            }
            text.dataoff {
                font-size: 0.55em;
                text-anchor: middle;
                fill: darkgray;
                font-weight: normal;
            }

            /* dimmed channel segments: source */

            rect.srcoff {
                fill: white;
                stroke: silver;
            }
            line.srcoff {
                stroke-width: 3;
                stroke: silver;
            }
            polygon.srcoff {
                fill: silver;
            }
            text.srcoff {
                font-size: 0.55em;
                text-anchor: middle;
                fill: darkgray;
                font-weight: normal;
            }

            /* dimmed channel segments: destination */

            rect.dstoff {
                fill: white;
                stroke: silver;
            }
            line.dstoff {
                stroke-width: 3;
                stroke: silver;
            }
            polygon.dstoff {
                fill: silver;
            }
            text.dstoff {
                font-size: 0.55em;
                text-anchor: middle;
                fill: darkgray;
                font-weight: normal;
            }

            /* dimmed channel segments: response */

            rect.rspoff {
                fill: white;
                stroke: white;
            }
            line.rspoff {
                stroke-width: 1;
                stroke: silver;
            }
            polyline.rspoff {
                stroke-width: 3;
                stroke: silver;
                fill: none;
            }
            ellipse.rspoff {
                fill: white;
                stroke: silver;
            }
            polygon.rspoff {
                fill: silver;
            }
            text.rspoff {
                font-size: 0.45em;
                text-anchor: left;
                fill: darkgray;
                font-weight: normal;
            }

            /* active deploy/undeploy */

            rect.deployon {
                fill: #005C5C;
                stroke: #005C5C;
            }
            text.deployon {
                font-size: 0.55em;
                text-anchor: middle;
                fill: white;
                font-weight: bold;
            }

            /* active data in */

            rect.dataon {
                fill: #FFC72C;
                stroke: #FFC72C;
            }
            line.dataon {
                stroke-width: 5;
                stroke: #FFC72C;
            }
            polygon.dataon {
                fill: #FFC72C;
              }
            text.dataon {
                font-size: 0.55em;
                text-anchor: middle;
                fill: #090909;
                font-weight: bold;
            }

            /* active channel segments: source */

            rect.srcon {
                fill: #CE1141;
                stroke: #CE1141;
            }
            line.srcon {
                stroke-width: 5;
                stroke: #CE1141;
            }
            polygon.srcon {
                fill: #CE1141;
              }
            text.srcon {
                font-size: 0.55em;
                text-anchor: middle;
                fill: white;
                font-weight: bold;
            }

            /* active channel segments: destination */

            rect.dston {
                fill: #004687;
                stroke: #004687;
            }
            line.dston {
                stroke-width: 5;
                stroke: #004687;
            }
            polygon.dston {
                fill: #004687;
            }
            text.dston {
                font-size: 0.55em;
                text-anchor: middle;
                fill: white;
                font-weight: bold;
            }

            /* active channel segments: response */

            rect.rspon {
                fill: #FFC72C;
                stroke: #FFC72C;
            }
            line.rspon {
                stroke-width: 1;
                stroke: #FFC72C;
            }
            polyline.rspon {
                stroke-width: 5;
                stroke: #FFC72C;
                fill: none;
            }
            ellipse.rspon {
                fill: #FFC72C;
                stroke: #FFC72C;
            }
            polygon.rspon {
                fill: #FFC72C;
            }
            text.rspon {
                font-size: 0.45em;
                text-anchor: left;
                fill: #090909;
                font-weight: bold;
            }
		]]></style>
	</xsl:template>
	
</xsl:stylesheet>


____________________________________________________________
Step 2 HTML description		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- @apiinfo """32 lines. Outputs minimal basic info about the channel.""" -->

     <xsl:template match="/" > 	
     	<xsl:apply-templates select="*|text()" mode="descrip" />
     </xsl:template>

	<xsl:template match="/channel" mode="descrip">
		<table cellpadding="6px 6px 6px 0px" border="0" class="notes" style="margin-top:12px;margin-bottom:20px;" >
			<xsl:variable name="textvalue"><xsl:value-of select="description/text()" /></xsl:variable>
			<xsl:variable name="timemillis"><xsl:value-of select="exportData/metadata/lastModified/time/text()" /></xsl:variable>
			<tr valign="bottom"><th>Last Modified</th><td>
			<xsl:call-template name="millisecs-to-ISO">
				<xsl:with-param name="millisecs" select="$timemillis" />
			</xsl:call-template>
			<xsl:text> (UTC)</xsl:text>
			</td></tr>
			<tr><th>ID</th><td><xsl:value-of select="id/text()" /></td></tr>
			<xsl:if test="(string-length($textvalue) > 0)">			
				<tr>
					<th valign="top">Description</th>
					<td valign="top"><xsl:value-of select="$textvalue" /></td>
				</tr>
			</xsl:if>	
			<tr>
				<th valign="top">Resources Loaded</th>
				<td valign="top">
					<xsl:apply-templates select="properties/resourceIds/entry/string[2]" mode="descripstring" />
				</td>
			</tr>
			<tr><th>NextGen&#174;</th><td><xsl:value-of select="@version" /></td></tr>
			<tr>
				<th valign="top">See Also</th>
				<td valign="top">
					Mirth&#174; Connect by NextGen Healthcare User Guide for version 3.11, The Message Processing Lifecycle, in
					<a href="https://www.nextgen.com/-/media/files/nextgen-connect/nextgen-connect-311-user-guide.pdf">nextgen-connect-311-user-guide.pdf</a>
				</td>
			</tr>
		</table>
	</xsl:template>
	
	<xsl:template match="/channel/properties/resourceIds/entry/string" mode="descripstring">
     	<xsl:value-of select="text()" />
     	<br/>
	</xsl:template>

	<xsl:template name="millisecs-to-ISO">
		<!-- https://stackoverflow.com/questions/27990478/convert-epoch-to-date-via-xsl-from-xml-attribute-and-display-in-html/27993455#27993455 -->
		<xsl:param name="millisecs"/>
		
		<xsl:param name="JDN" select="floor($millisecs div 86400000) + 2440588"/>
		<xsl:param name="mSec" select="$millisecs mod 86400000"/>
		
		<xsl:param name="f" select="$JDN + 1401 + floor((floor((4 * $JDN + 274277) div 146097) * 3) div 4) - 38"/>
		<xsl:param name="e" select="4*$f + 3"/>
		<xsl:param name="g" select="floor(($e mod 1461) div 4)"/>
		<xsl:param name="h" select="5*$g + 2"/>
		
		<xsl:param name="d" select="floor(($h mod 153) div 5 ) + 1"/>
		<xsl:param name="m" select="(floor($h div 153) + 2) mod 12 + 1"/>
		<xsl:param name="y" select="floor($e div 1461) - 4716 + floor((14 - $m) div 12)"/>
		
		<xsl:param name="H" select="floor($mSec div 3600000)"/>
		<xsl:param name="M" select="floor($mSec mod 3600000 div 60000)"/>
		<xsl:param name="S" select="$mSec mod 60000 div 1000"/>
		
		<xsl:value-of select="concat($y, format-number($m, '-00'), format-number($d, '-00'))" />
		<xsl:value-of select="concat(format-number($H, 'T00'), format-number($M, ':00'), format-number($S, ':00'))" />
	</xsl:template> 

	<xsl:template match="text()" mode="descrip" />
	<xsl:template match="text()" mode="descripstring" />

</xsl:stylesheet>

____________________________________________________________
Step 3 HTML diagram header		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="headdiagram" />
     </xsl:template>

	<xsl:template match="/channel/name" mode="headdiagram">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<h2><xsl:value-of select="$textvalue"/>: Channel Flow Diagram</h2>
     </xsl:template>

	<xsl:template match="text()" mode="headdiagram" />

</xsl:stylesheet>


____________________________________________________________
Step 4 HTML details header		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="headddetails" />
     </xsl:template>

	<xsl:template match="/channel/name" mode="headddetails">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<h2><xsl:value-of select="$textvalue"/>: Channel Flow Details</h2>
     </xsl:template>

	<xsl:template match="text()" mode="headddetails" />

</xsl:stylesheet>

____________________________________________________________
Step 5 HTML details table		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- @apiinfo """450 lines. Outputs a human-readable overview (HTML table) of full channel flow with API calls out highlighted.""" -->

	<xsl:template match="/">
		<table cellpadding="6px" border="1" class="notes">
			<tr>
				<th>Data<br/>Type</th>
				<th>Channel Segment</th>
				<th>Segment Type or Class</th>
				<th>ID</th>
				<th>Step</th>
				<th>Name</th>
				<th>API Calls Out, Other Notes</th>
			</tr>
     		<xsl:apply-templates select="/channel/deployScript" mode="details" />
 			<xsl:apply-templates select="/channel/sourceConnector" mode="detailssrcdata" />
 			<xsl:apply-templates select="/channel/sourceConnector/transformer" mode="detailssrcintype" />
			<xsl:apply-templates select="/channel/sourceConnector" mode="detailssrcconn" />
			<xsl:apply-templates select="/channel/properties/attachmentProperties" mode="detailssrcatt" />
			<xsl:apply-templates select="/channel/preprocessingScript" mode="detailssrcpre" />
			<xsl:apply-templates select="/channel/sourceConnector/filter/elements" mode="detailssrcfil" />
			<xsl:apply-templates select="/channel/sourceConnector/transformer/elements" mode="detailssrcxform" />
 			<xsl:apply-templates select="/channel/sourceConnector/transformer" mode="detailssrcouttype" />
   			<xsl:apply-templates select="/channel/destinationConnectors" mode="detailsdst" />
   			<xsl:apply-templates select="/channel/sourceConnector/properties" mode="detailssrcpost" />
   			<!-- TODO: (as needed) <xsl:apply-templates select="/channel/sourceConnector/properties" mode="detailssrcselrsp" />-->
   			<xsl:apply-templates select="/channel/sourceConnector" mode="detailssrcrsp" />
		    	<xsl:apply-templates select="/channel/undeployScript" mode="details" />
		</table>
     </xsl:template>

     <!-- deploy -->

	<xsl:template match="/channel/deployScript" mode="details">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:if test="(string-length($textvalue) > 0) and not ($textvalue = '// This script executes once when the channel is deployed // You only have access to the globalMap and globalChannelMap here to persist data return;')">
			<xsl:variable name="apiinfo">
				<xsl:call-template name="getapiinfo">
					<xsl:with-param name="textvalue" select="$textvalue" />
				</xsl:call-template>
			</xsl:variable>
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="deployon" /></svg>&#160;Deploy Script
				</td>
				<td>JavaScript</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td><xsl:value-of select="$apiinfo" /></td>
			</tr>
			<tr>
				<td class="raw" colspan="7">&#160;</td>
			</tr>
		</xsl:if>
     </xsl:template>

	<!-- channel segments: source in -->

	<xsl:template match="/channel/sourceConnector" mode="detailssrcdata">
		<xsl:variable name="type"><xsl:value-of select="properties/@class" /></xsl:variable>
		<xsl:if test="not (contains($type, 'JavaScriptReceiver'))">
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="dataon" /></svg>&#160;Data Source
				</td>
				<td class="raw" colspan="5">&#160;</td>
			</tr>
		</xsl:if>
     </xsl:template>

	<xsl:template match="/channel/sourceConnector/transformer" mode="detailssrcintype">
            <tr>
                <td><xsl:value-of select="inboundDataType/text()" /></td>
                <td class="raw" colspan="6">&#160;</td>
            </tr>  
   	</xsl:template>

	<xsl:template match="/channel/sourceConnector" mode="detailssrcconn">
		<xsl:variable name="type"><xsl:value-of select="properties/@class" /></xsl:variable>
		<xsl:variable name="useScript"><xsl:value-of select="properties/useScript/text()" /></xsl:variable>
		<xsl:variable name="detail">
			<xsl:choose>
				<xsl:when test="(contains($type, 'DatabaseReceiver'))"><xsl:value-of select="properties/driver/text()" /></xsl:when>
				<xsl:otherwise></xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<xsl:variable name="language">
			<xsl:choose>
				<xsl:when test="(contains($type, 'JavaScriptReceiver'))"></xsl:when>
				<xsl:otherwise>
					<xsl:choose>
						<xsl:when test="($useScript = 'true')"> (JavaScript) </xsl:when>
						<xsl:when test="($useScript = 'false')"> (SQL) </xsl:when>
						<xsl:otherwise></xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<tr>
			<td class="raw">&#160;</td>
			<td>
				<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="srcon" /></svg>&#160;Source Connector
			</td>
			<td><xsl:value-of select="transportName/text()" /><xsl:text> </xsl:text><xsl:value-of select="$language" /><xsl:text> </xsl:text><xsl:value-of select="$detail" /></td>
			<td><xsl:value-of select="metaDataId/text()" /></td>
			<td>&#160;</td>
			<td><xsl:value-of select="name/text()" /></td>
			<td>
				<xsl:choose>
					<xsl:when test="(contains($type, 'JavaScriptReceiver'))">
						<xsl:variable name="textvalue"><xsl:value-of select="properties/script/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>
					<xsl:when test="(contains($type, 'DatabaseReceiver'))">				
						<xsl:variable name="select"><xsl:value-of select="properties/select/text()" /></xsl:variable>
						<xsl:variable name="update"><xsl:value-of select="properties/update/text()" /></xsl:variable>
						<xsl:variable name="selectinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$select" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:variable name="updateinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$update" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$selectinfo" /> 
						<xsl:choose>
							<xsl:when test="(string-length($updateinfo) > 0)">
								<br/><xsl:value-of select="$updateinfo" />
							</xsl:when>
							<xsl:otherwise></xsl:otherwise>
						</xsl:choose>
					</xsl:when>
					<xsl:when test="(contains($type, 'FileReceiver'))"> 
						Input folder/filename: 						<xsl:value-of select="properties/host/text()" />/<xsl:value-of select="properties/fileFilter/text()" />
					</xsl:when>
					<xsl:otherwise>
						&#160;
					</xsl:otherwise>
				</xsl:choose>
			</td>
		</tr>
	</xsl:template>

	<xsl:template match="/channel/properties/attachmentProperties" mode="detailssrcatt">
		<xsl:variable name="type"><xsl:value-of select="type/text()" /></xsl:variable>
		<xsl:if test="(string-length($type) > 0) and (not ($type = 'None'))">
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="srcon" /></svg>&#160;Attachment Handler
				</td>
				<td><xsl:value-of select="$type" /></td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td>
					<xsl:choose>
						<xsl:when test="(contains($type, 'JavaScript'))">
							<xsl:apply-templates select="properties/entry" mode="detailssrcattjs" />
						</xsl:when>
						<xsl:otherwise>&#160;</xsl:otherwise>
					</xsl:choose>
				</td>
			</tr>
		</xsl:if>
	</xsl:template>

	<xsl:template match="/channel/properties/attachmentProperties/properties/entry" mode="detailssrcattjs">
		<xsl:apply-templates select="string" mode="detailssrcattjs" />
	</xsl:template>

	<xsl:template match="/channel/properties/attachmentProperties/properties/entry/string" mode="detailssrcattjs">
		<xsl:variable name="textvalue"><xsl:value-of select="text()" /></xsl:variable>
		<xsl:variable name="apiinfo">
			<xsl:call-template name="getapiinfo">
				<xsl:with-param name="textvalue" select="$textvalue" />
			</xsl:call-template>
		</xsl:variable>
		<xsl:if test="(not ($apiinfo = '&#160;'))">
			<xsl:value-of select="$apiinfo" /><xsl:text> </xsl:text>
		</xsl:if>
	</xsl:template>

	<xsl:template match="/channel/preprocessingScript" mode="detailssrcpre">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:if test="(string-length($textvalue) > 0) and not ($textvalue = '// Modify the message variable below to pre process data return message;')">
			<xsl:variable name="apiinfo">
				<xsl:call-template name="getapiinfo">
					<xsl:with-param name="textvalue" select="$textvalue" />
				</xsl:call-template>
			</xsl:variable>
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="srcon" /></svg>&#160;Preprocessor Script
				</td>
				<td>JavaScript</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td><xsl:value-of select="$apiinfo" /></td>
			</tr>
		</xsl:if>
 	</xsl:template>

	<!-- channel segments: source transform -->

	<xsl:template match="/channel/sourceConnector/filter/elements" mode="detailssrcfil" >
		<xsl:variable name="stepcount"><xsl:value-of select="count(*)" /></xsl:variable>
		<xsl:if test="$stepcount > 0">
     		<xsl:apply-templates select="*" mode="detailssrcfilelement" />
		</xsl:if>
	</xsl:template>
		
	<xsl:template match="*" mode="detailssrcfilelement">
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>
		<tr>
			<td class="raw">&#160;</td>
			<td>
				<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="srcon" /></svg>&#160;Source Filter
			</td>
			<td><xsl:value-of select="substring-after($type, 'com.mirth.connect.')" /></td>
			<td>&#160;</td>
			<td><xsl:value-of select="sequenceNumber/text()" /></td>
			<td><xsl:value-of select="name/text()" /></td>
			<td>
				<xsl:choose>
					<xsl:when test="(contains($type, 'JavaScript'))">
						<xsl:variable name="textvalue"><xsl:value-of select="script/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>
					<xsl:when test="(contains($type, 'Xslt'))">
						<xsl:variable name="textvalue"><xsl:value-of select="template/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>				
					<xsl:otherwise>
						&#160;
					</xsl:otherwise>
				</xsl:choose>
			</td>
            </tr>
	</xsl:template>

	<xsl:template match="/channel/sourceConnector/transformer/elements" mode="detailssrcxform" >
     	<xsl:apply-templates select="*" mode="detailssrcxformelement" />
	</xsl:template>
		
	<xsl:template match="*" mode="detailssrcxformelement">
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>
		<tr>
			<td class="raw">&#160;</td>
			<td>
				<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="srcon" /></svg>&#160;Source Transformer
			</td>
			<td><xsl:value-of select="substring-after($type, 'com.mirth.connect.')" /></td>
			<td>&#160;</td>
			<td><xsl:value-of select="sequenceNumber/text()" /></td>
			<td><xsl:value-of select="name/text()" /></td>
			<td>
				<xsl:choose>
					<xsl:when test="(contains($type, 'JavaScript'))">
						<xsl:variable name="textvalue"><xsl:value-of select="script/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>
					<xsl:when test="(contains($type, 'Xslt'))">
						<xsl:variable name="textvalue"><xsl:value-of select="template/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfoxml">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>				
					<xsl:otherwise>
						&#160;
					</xsl:otherwise>
				</xsl:choose>
			</td>
		</tr>
	</xsl:template>
	
   	<xsl:template match="/channel/sourceConnector/transformer" mode="detailssrcouttype">
            <tr>
                <td><xsl:value-of select="outboundDataType/text()" /></td>
                <td class="raw" colspan="6">&#160;</td>
            </tr>  
   	</xsl:template>

  	<!-- destinations -->

  	<xsl:template match="/channel/destinationConnectors" mode="detailsdst">
 		<xsl:apply-templates select="connector" mode="detailsdst" />
   	</xsl:template>

    	<xsl:template match="/channel/destinationConnectors/connector" mode="detailsdst">
 		<xsl:apply-templates select="transformer" mode="detailsdstintype" />
 		<xsl:apply-templates select="filter" mode="detailsdstfil" />
 		<xsl:apply-templates select="transformer" mode="detailsdstxform" />		
		<xsl:call-template name="getdstconn">
			<xsl:with-param name="connector" select="." />
		</xsl:call-template>		
  		<!-- TODO: (when needed) <xsl:apply-templates select="responseTransformer" mode="detailsdstrsp" />-->
 		<xsl:apply-templates select="transformer" mode="detailsdstouttype" />
   	</xsl:template>
   	
 	<xsl:template match="/channel/destinationConnectors/connector/transformer" mode="detailsdstintype">
		<tr>
			<td><xsl:value-of select="inboundDataType/text()" /></td>
			<td class="raw" colspan="6">&#160;</td>
		</tr>  
	</xsl:template>

 	<!-- channel segments: destination transforms -->

	<xsl:template match="/channel/destinationConnectors/connector/filter" mode="detailsdstfil" >
     	<xsl:apply-templates select="elements" mode="detailsdstfilelement" />
   	</xsl:template>

	<xsl:template match="/channel/destinationConnectors/connector/filter/elements" mode="detailsdstfilelement" >
     	<xsl:apply-templates select="*" mode="detailsdstfilelementout" />
	</xsl:template>
		
	<xsl:template match="*" mode="detailsdstfilelementout">
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>
		<tr>
			<td class="raw">&#160;</td>
			<td>
				<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="dston" /></svg>&#160;Destination Filter
			</td>
			<td><xsl:value-of select="substring-after($type, 'com.mirth.connect.')" /></td>
			<td>&#160;</td>
			<td><xsl:value-of select="sequenceNumber/text()" /></td>
			<td><xsl:value-of select="name/text()" /></td>
			<td>
				<xsl:choose>
					<xsl:when test="(contains($type, 'JavaScript'))">
						<xsl:variable name="textvalue"><xsl:value-of select="script/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>
					<xsl:when test="(contains($type, 'Xslt'))">
						<xsl:variable name="textvalue"><xsl:value-of select="template/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>				
					<xsl:otherwise>
						&#160;
					</xsl:otherwise>
				</xsl:choose>
			</td>
		</tr>
	</xsl:template>

	<xsl:template match="/channel/destinationConnectors/connector/transformer" mode="detailsdstxform" >
     	<xsl:apply-templates select="elements" mode="detailsdstxformelement" />
   	</xsl:template>

	<xsl:template match="/channel/destinationConnectors/connector/transformer/elements" mode="detailsdstxformelement" >
	    	<xsl:apply-templates select="*" mode="detailsdstxformelementout" />
	</xsl:template>
		
	<xsl:template match="*" mode="detailsdstxformelementout">
 		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>
		<tr>
			<td class="raw">&#160;</td>
			<td>
				<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="dston" /></svg>&#160;Destination Transformer
			</td>
			<td><xsl:value-of select="substring-after($type, 'com.mirth.connect.')" /></td>
			<td>&#160;</td>
			<td><xsl:value-of select="sequenceNumber/text()" /></td>
			<td><xsl:value-of select="name/text()" /></td>
			<td>
				<xsl:choose>
					<xsl:when test="(contains($type, 'JavaScript'))">
						<xsl:variable name="textvalue"><xsl:value-of select="script/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>
					<xsl:when test="(contains($type, 'Xslt'))">
						<xsl:variable name="textvalue"><xsl:value-of select="template/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>				
					<xsl:otherwise>
						&#160;
					</xsl:otherwise>
				</xsl:choose>
			</td>
		</tr>
	</xsl:template>

	<!-- channel segments: destination out -->
	
	<xsl:template name="getdstconn">
		<xsl:param name="connector" />
		<xsl:variable name="type"><xsl:value-of select="$connector/properties/@class" /></xsl:variable>
		<xsl:variable name="useScript"><xsl:value-of select="$connector/properties/useScript/text()" /></xsl:variable>
		<xsl:variable name="detail">
			<xsl:choose>
				<xsl:when test="(contains($type, 'DatabaseDispatcher'))"> <xsl:value-of select="$connector/properties/driver/text()" /></xsl:when>
				<xsl:otherwise></xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<xsl:variable name="language">
			<xsl:choose>
				<xsl:when test="(contains($type, 'JavaScriptDispatcher'))"></xsl:when>
				<xsl:otherwise>
					<xsl:choose>
						<xsl:when test="($useScript = 'true')"> (JavaScript) </xsl:when>
						<xsl:when test="($useScript = 'false')"> (SQL) </xsl:when>
						<xsl:otherwise></xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<tr>
			<td class="raw">&#160;</td>
			<td>
				<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="dston" /></svg>&#160;Destination Connector
			</td>
			<td><xsl:value-of select="$connector/transportName/text()" /><xsl:text> </xsl:text><xsl:value-of select="$language" /><xsl:text> </xsl:text><xsl:value-of select="$detail" /> </td>
			<td><xsl:value-of select="$connector/metaDataId/text()" /></td>
			<td>&#160;</td>
			<td><xsl:value-of select="$connector/name/text()" /></td>
			<td>
				<xsl:choose>
					<xsl:when test="(contains($type, 'JavaScriptDispatcher'))">
						<xsl:variable name="textvalue"><xsl:value-of select="$connector/properties/script/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>				
					<xsl:when test="(contains($type, 'DatabaseDispatcher'))">				
						<xsl:variable name="textvalue"><xsl:value-of select="$connector/properties/query/text()" /></xsl:variable>
						<xsl:variable name="apiinfo">
							<xsl:call-template name="getapiinfo">
								<xsl:with-param name="textvalue" select="$textvalue" />
							</xsl:call-template>
						</xsl:variable>
						<xsl:value-of select="$apiinfo" />
					</xsl:when>
					<xsl:when test="(contains($type, 'FileDispatcher'))"> 
						Output folder/filename: 						<xsl:value-of select="$connector/properties/host/text()" />/<xsl:value-of select="properties/outputPattern/text()" />
					</xsl:when>
					<xsl:otherwise>
						&#160;
					</xsl:otherwise>
				</xsl:choose>
			</td>
		</tr>
    	</xsl:template>

  	<xsl:template match="/channel/destinationConnectors/connector/transformer" mode="detailsdstouttype">
		<tr>
			<td><xsl:value-of select="outboundDataType/text()" /></td>
			<td class="raw" colspan="6">&#160;</td>
		</tr>  
   	</xsl:template>

	<!-- TODO: (when needed) channel segments: destination response -->

	<!-- channel segments: source out -->   	

	<xsl:template match="/channel/postprocessingScript" mode="detailssrcpost">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:if test="(string-length($textvalue) > 0) and not ($textvalue = '// This script executes once after a message has been processed // Responses returned from here will be stored as &quot;Postprocessor&quot; in the response map return;')">
			<xsl:variable name="apiinfo">
				<xsl:call-template name="getapiinfo">
					<xsl:with-param name="textvalue" select="$textvalue" />
				</xsl:call-template>
			</xsl:variable>
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="srcon" /></svg>&#160;Postprocessor Script
				</td>
				<td>JavaScript</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td><xsl:value-of select="$apiinfo" /></td>
			</tr>
		</xsl:if>
 	</xsl:template>

	<xsl:template match="/channel/sourceConnector/properties" mode="detailssrcrsp">
		<xsl:variable name="type"><xsl:value-of select="@class" /></xsl:variable>
		<xsl:variable name="srcrsp"><xsl:value-of select="sourceConnectorProperties/responseVariable/text()" /></xsl:variable>
		<xsl:if test="not (($srcrsp = 'None') or (contains($type, 'JavaScriptReceiver')))">
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="dataon" /></svg>&#160;Response
				</td>
				<td><xsl:value-of select="$srcrsp" /></td>
				<td class="raw" colspan="4">&#160;</td>
			</tr>
		</xsl:if>
     </xsl:template>

     <!-- undeploy -->

	<xsl:template match="/channel/undeployScript" mode="details">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:if test="(string-length($textvalue) > 0) and not ($textvalue = '// This script executes once when the channel is undeployed // You only have access to the globalMap and globalChannelMap here to persist data return;')">
			<xsl:variable name="apiinfo">
				<xsl:call-template name="getapiinfo">
					<xsl:with-param name="textvalue" select="$textvalue" />
				</xsl:call-template>
			</xsl:variable>
			<tr>
				<td class="raw" colspan="7">&#160;</td>
			</tr>
			<tr>
				<td class="raw">&#160;</td>
				<td>
					<svg width="15px" height="12px" viewBox="0 0 15 15"><rect x="0" y="0" width="15" height="15" class="deployon" /></svg>&#160;Undeploy Script
				</td>
				<td>JavaScript</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td>&#160;</td>
				<td><xsl:value-of select="$apiinfo" /></td>
			</tr>
		</xsl:if>
     </xsl:template>

	<!-- utility templates -->
	
     <xsl:template name="getapiinfo" >
     	<xsl:param name="textvalue" />
		<xsl:variable name="text1"><xsl:value-of select="substring-after($textvalue, '@apiinfo')" /></xsl:variable>
		<xsl:variable name="text2"><xsl:value-of select="substring-after($text1, '&quot;&quot;&quot;')" /></xsl:variable>
		<xsl:variable name="text3"><xsl:value-of select="substring-before($text2, '&quot;&quot;&quot;')" /></xsl:variable>
		<xsl:choose>
			<xsl:when test="contains($textvalue, '@apiinfo')"><xsl:value-of select="$text3" /></xsl:when>
			<xsl:otherwise>&#160;</xsl:otherwise>
		</xsl:choose>   	
     </xsl:template>

     <xsl:template name="getapiinfoxml" >
     	<xsl:param name="textvalue" />
		<xsl:variable name="text1"><xsl:value-of select="substring-after($textvalue, '@apiinfo')" /></xsl:variable>
		<xsl:variable name="text2"><xsl:value-of select="substring-after($text1, '&amp;quot;&amp;quot;&amp;quot;')" /></xsl:variable>
		<xsl:variable name="text3"><xsl:value-of select="substring-before($text2, '&amp;quot;&amp;quot;&amp;quot;')" /></xsl:variable>
		<xsl:choose>
			<xsl:when test="contains($textvalue, '@apiinfo')"><xsl:value-of select="$text3" /></xsl:when>
			<xsl:otherwise>&#160;</xsl:otherwise>
		</xsl:choose>   	
     </xsl:template>

	<!-- default text nodes -->
	
	<xsl:template match="text()" mode="details" />
	<xsl:template match="text()" mode="detailssrcdata" />
	<xsl:template match="text()" mode="detailssrcintype" />
	<xsl:template match="text()" mode="detailssrcconn" />
	<xsl:template match="text()" mode="detailssrcatt" />
	<xsl:template match="text()" mode="detailssrcattjs" />
	<xsl:template match="text()" mode="detailssrcpre" />
	<xsl:template match="text()" mode="detailssrcfil" />
	<xsl:template match="text()" mode="detailssrcfilelement" />
	<xsl:template match="text()" mode="detailssrcxform" />
	<xsl:template match="text()" mode="detailssrcxformelement" />	
	<xsl:template match="text()" mode="detailssrcouttype" />
	<xsl:template match="text()" mode="detailsdst" />
	<xsl:template match="text()" mode="detailsdstintype" />
	<xsl:template match="text()" mode="detailsdstfil" />
	<xsl:template match="text()" mode="detailsdstfilelement" />
	<xsl:template match="text()" mode="detailsdstfilelementout" />
	<xsl:template match="text()" mode="detailsdstxform" />
	<xsl:template match="text()" mode="detailsdstxformelement" />
	<xsl:template match="text()" mode="detailsdstxformelementout" />
	<xsl:template match="text()" mode="detailsdstconn" />	
	<!-- TODO: (when needed) <xsl:template match="text()" mode="detailsdstxformrsp" />-->	
	<xsl:template match="text()" mode="detailsdstouttype" />
	<xsl:template match="text()" mode="detailssrcpost" />
	<!-- TODO: (when needed) <xsl:template match="text()" mode="detailssrcselrsp" />-->	
	<xsl:template match="text()" mode="detailssrcrsp" />
	<!-- TODO: (when needed) <xsl:template match="text()" mode="detailsdstrsp" />-->	
</xsl:stylesheet>


____________________________________________________________
Step 6 SVG background panels		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="/">
		<rect id="srcbg" rx="0" ry="0" x="48" y="-5" width="572" height="295" class="srcbg" />
		<rect id="dstbg" rx="0" ry="0" x="133" y="110" width="487" height="180" class="dstbg" />
		<rect id="srcbgxform" rx="0" ry="0" x="290" y="40" width="320" height="60" class="xformpanel" />
		<rect id="dstbgxform" rx="0" ry="0" x="451" y="120" width="159" height="120" class="xformpanel" />
		<rect id="dstbgrsp" rx="0" ry="0" x="210" y="120" width="160" height="120" class="xformpanel" />
		<text id="bgtext1" x="60" y="12" class="bgbig">Source Processing</text>
		<text id="bgtext2" x="480" y="280" class="bgbig">Destination Processing</text>
     </xsl:template>

</xsl:stylesheet>


____________________________________________________________
Step 7 Deploy		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="deploy" />
     </xsl:template>

	<xsl:template match="/channel/deployScript" mode="deploy">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="(string-length($textvalue) > 0) and not ($textvalue = '// This script executes once when the channel is deployed // You only have access to the globalMap and globalChannelMap here to persist data return;')">deployon</xsl:when>
				<xsl:otherwise>deployoff</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<rect id="deploy" rx="1" ry="1" x="0" y="190" width="40" height="40" class="{$style}" />
		<text id="deploytext1" x="20" y="208" class="{$style}">Deploy</text>
		<text id="deploytext2" x="20" y="218" class="{$style}">Script</text>
     </xsl:template>

	<xsl:template match="text()" mode="deploy" />

</xsl:stylesheet>


____________________________________________________________
Step 8 Undeploy		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="undeploy" />
     </xsl:template>

	<xsl:template match="/channel/undeployScript" mode="undeploy">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="(string-length($textvalue) > 0) and not ($textvalue = '// This script executes once when the channel is undeployed // You only have access to the globalMap and globalChannelMap here to persist data return;')">deployon</xsl:when>
				<xsl:otherwise>deployoff</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<rect id="undeploy" rx="1" ry="1" x="0" y="250" width="40" height="40" class="{$style}" />
		<text id="undeploytext1" x="20" y="268" class="{$style}">Undeploy</text>
		<text id="undeploytext2" x="20" y="278" class="{$style}">Script</text>
     </xsl:template>

	<xsl:template match="text()" mode="undeploy" />

</xsl:stylesheet>


____________________________________________________________
Step 9 Data Source		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcdata" />
     </xsl:template>

	<xsl:template match="/channel/sourceConnector" mode="srcdata">
		<xsl:variable name="type"><xsl:value-of select="properties/@class" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="contains($type, 'JavaScriptReceiver')">dataoff</xsl:when>
				<xsl:otherwise>dataon</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<line id="srcdataline" x1="40" y1="70" x2="55" y2="70" class="{$style}" />
		<polygon id="srcdatalineptr" points="54 63, 60 70, 54 77" class="{$style}" />
		<rect id="srcdata" rx="1" ry="1" x="0" y="50" width="40" height="40" class="{$style}" />
		<text id="srcdatatext1" x="20" y="68" class="{$style}">Data</text>
		<text id="srcdatatext2" x="20" y="78" class="{$style}">Source</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcdata" />

</xsl:stylesheet>


____________________________________________________________
Step 10 Source Connector		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcconn" />
     </xsl:template>

	<xsl:template match="/channel/sourceConnector" mode="srcconn">
		<xsl:variable name="type"><xsl:value-of select="properties/@class" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="(string-length($type) > 0)">srcon</xsl:when>
				<xsl:otherwise>srcoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<line id="srcconnline" x1="120" y1="70" x2="135" y2="70" class="{$style}" />
		<polygon id="srcconnlineptr" points="134 63, 140 70, 134 77" class="{$style}" />
		<rect id="srcconn" rx="1" ry="1" x="60" y="50" width="60" height="40" class="{$style}" />
		<line id="srcconnline" x1="120" y1="70" x2="135" y2="70" class="{$style}" />
		<polygon id="srcconnlineptr" points="134 63, 140 70, 134 77" class="{$style}" />
		<text id="srcconntext1" x="90" y="68" class="{$style}">Source</text>
		<text id="srcconntext2" x="90" y="78" class="{$style}">Connector</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcconn" />

</xsl:stylesheet>


____________________________________________________________
Step 11 Attachment Handler		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcatt" />
     </xsl:template>

	<xsl:template match="/channel/properties/attachmentProperties" mode="srcatt">
		<xsl:variable name="type"><xsl:value-of select="type/text()" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="(string-length($type) > 0) and (not ($type = 'None'))">srcon</xsl:when>
				<xsl:otherwise>srcoff</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<line id="srcattline" x1="200" y1="70" x2="215" y2="70" class="{$style}" />
		<polygon id="srcattlineptr" points="214 63, 220 70, 214 77" class="{$style}" />
		<rect id="srcatt" rx="1" ry="1" x="140" y="50" width="60" height="40" class="{$style}" />
		<text id="srcatttext1" x="170" y="68" class="{$style}">Attachment</text>
		<text id="srcatttext2" x="170" y="78" class="{$style}">Handler</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcatt" />

</xsl:stylesheet>


____________________________________________________________
Step 12 Preprocessor		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcpre" />
     </xsl:template>

	<xsl:template match="/channel/preprocessingScript" mode="srcpre">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="(string-length($textvalue) > 0) and not ($textvalue = '// Modify the message variable below to pre process data return message;')">srcon</xsl:when>
				<xsl:otherwise>srcoff</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<line id="srcpreline" x1="280" y1="70" x2="295" y2="70" class="{$style}" />
		<polygon id="srcprelineptr" points="294 63, 300 70, 294 77" class="{$style}" />
		<rect id="srcpre" rx="1" ry="1" x="220" y="50" width="60" height="40" class="{$style}" />
		<text id="srcpretext1" x="250" y="68" class="{$style}">Preprocessor</text>
		<text id="srcpretext2" x="250" y="78" class="{$style}">Script</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcpre" />

</xsl:stylesheet>


____________________________________________________________
Step 13 Source Serialize		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcser" />
     </xsl:template>

	<xsl:template match="/channel/sourceConnector" mode="srcser">
		<!-- TODO: (when needed) research XSL to toggle srcoff/srcon -->
		<line id="srcserline" x1="360" y1="70" x2="375" y2="70" class="srcoff" />
		<polygon id="srcserlineptr" points="374 63, 380 70, 374 77" class="srcoff" />
		<rect id="srcser" rx="1" ry="1" x="300" y="50" width="60" height="40" class="srcoff" />
		<text id="srcsertext1" x="330" y="68" class="srcoff">Serialized</text>
		<text id="srcsertext2" x="330" y="78" class="srcoff">to XML</text>
    </xsl:template>

	<xsl:template match="text()" mode="srcser" />

</xsl:stylesheet>


____________________________________________________________
Step 14 Source Deserialize		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcdes" />
     </xsl:template>

	<xsl:template match="/channel/sourceConnector" mode="srcdes">
		<!-- TODO: (when needed) research XSL to toggle srcoff/srcon -->
		<line id="srcdesline" x1="570" y1="90" x2="570" y2="125" class="srcoff" />
		<polygon id="srcdeslineptr" points="563 124, 570 130, 577 124" class="srcoff" />
		<rect id="srcdes" rx="1" ry="1" x="540" y="50" width="60" height="40" class="srcoff" />
		<text id="srcdestext1" x="570" y="68" class="srcoff">Deserialized</text>
		<text id="srcdestext2" x="570" y="78" class="srcoff">e.g., to HL7</text>
    </xsl:template>

	<xsl:template match="text()" mode="srcdes" />

</xsl:stylesheet>

____________________________________________________________
Step 15 Source Filter		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcfil" />
     </xsl:template>

    <xsl:template match="/channel/sourceConnector/filter/elements" mode="srcfil" >
		<xsl:variable name="stepcount"><xsl:value-of select="count(*)" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="($stepcount > 0)">srcon</xsl:when>
				<xsl:otherwise>srcoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<line id="srcfilline" x1="440" y1="70" x2="455" y2="70" class="{$style}" />
		<polygon id="srcfillineptr" points="454 63, 460 70, 454 77" class="{$style}" />
		<rect id="srcfil" rx="1" ry="1" x="380" y="50" width="60" height="40" class="{$style}" />
		<text id="srcfiltext1" x="410" y="73" class="{$style}">Filter</text>
		<text id="srcfiltext2" x="410" y="78" class="{$style}"></text>
     </xsl:template>

 	<xsl:template match="text()" mode="srcfil" />

</xsl:stylesheet>


____________________________________________________________
Step 16 Source Transformer		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcxform" />
     </xsl:template>

    <xsl:template match="/channel/sourceConnector/transformer/elements" mode="srcxform" >
		<xsl:variable name="stepcount"><xsl:value-of select="count(*)" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="($stepcount > 0)">srcon</xsl:when>
				<xsl:otherwise>srcoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<line id="srcxformline" x1="520" y1="70" x2="535" y2="70" class="{$style}" />
		<polygon id="srcxformlineptr" points="534 63, 540 70, 534 77" class="{$style}" />
		<rect id="srcxform" rx="1" ry="1" x="460" y="50" width="60" height="40" class="{$style}" />
		<text id="srcxformtext1" x="490" y="73" class="{$style}">Transformer</text>
		<text id="srcxformtext2" x="490" y="78" class="{$style}"></text>
     </xsl:template>

 	<xsl:template match="text()" mode="srcxform" />

</xsl:stylesheet>


____________________________________________________________
Step 17 Destination Serialize		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="dstser" />
     </xsl:template>

	<xsl:template match="/channel/destinationConnectors" mode="dstser">
		<!-- TODO: (when needed) research XSL to toggle srcoff/srcon -->
		<line id="dstserline" x1="570" y1="170" x2="570" y2="185" class="dstoff" />
		<polygon id="dstserlineptr" points="563 184, 570 190, 577 184" class="dstoff" />
		<rect id="dstser" rx="1" ry="1" x="540" y="130" width="60" height="40" class="dstoff" />
		<text id="dstsertext1" x="570" y="148" class="dstoff">Serialized</text>
		<text id="dstsertext2" x="570" y="158" class="dstoff">to XML</text>
    </xsl:template>

	<xsl:template match="text()" mode="dstser" />

</xsl:stylesheet>


____________________________________________________________
Step 18 Destination Deserialize		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="dstdes" />
     </xsl:template>

	<xsl:template match="/channel/destinationConnectors" mode="dstdes">
		<!-- TODO: (when needed) research XSL to toggle srcoff/srcon -->
		<line id="dstdesline" x1="460" y1="210" x2="440" y2="210" class="dstoff" />
		<polygon id="dstdeslineptr" points="446 217, 440 210, 446 203" class="dstoff" />
		<rect id="dstdes" rx="1" ry="1" x="460" y="190" width="60" height="40" class="dstoff" />
		<text id="dstdestext1" x="490" y="208" class="dstoff">Deserialized</text>
		<text id="dstdestext2" x="490" y="218" class="dstoff">e.g., to HL7</text>
    </xsl:template>

	<xsl:template match="text()" mode="dstdes" />

</xsl:stylesheet>


____________________________________________________________
Step 19 Destination Response (all)		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- @apiinfo """Highlights not yet added, not yet needed.""" -->

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="xformdstrspall" />
     </xsl:template>

	<xsl:template match="/channel/destinationConnectors" mode="xformdstrspall">
		<!-- TODO: (when needed) research XSL to toggle srcoff/srcon and/or dstoff/dston -->
		<polyline id="dstconndataline" points="410 230, 410 260, 330 260, 330 235" class="rspoff" />
		<polygon id="dstrspdesdataptr" points="323 236, 330 230, 337 236" class="rspoff" />
		<ellipse id="dstrspdesdata1" cx="370" cy="270" rx="10" ry="5" class="rspoff" />
		<rect id="dstrspdesdata2" x="360" y="255" width="20" height="15" class="rspoff" />
		<ellipse id="dstrspdesdata3" cx="370" cy="255" rx="10" ry="5" class="rspoff" />
		<line id="dstrspdesdata4" x1="360" y1="256" x2="360" y2="271" class="rspoff" />
		<line id="dstrspdesdata5" x1="380" y1="256" x2="380" y2="271" class="rspoff" />
		<text id="dstconndata6" x="354" y="285" class="rspoff">Response</text>

		<line id="dstrspserline" x1="330" y1="175" x2="330" y2="190" class="dstoff" />
		<polygon id="dstrspserlineptr" points="323 176, 330 170, 337 176" class="dstoff" />
		<rect id="dstrspser" rx="1" ry="1" x="300" y="190" width="60" height="40" class="dstoff" />
		<text id="srcdestext1" x="330" y="208" class="dstoff">Serialized</text>
		<text id="srcdestext2" x="330" y="218" class="dstoff">to XML</text>
		
		<polyline id="dstrspxformdataline" points="300 150, 250 150, 250 185" class="rspoff" />
		<polygon id="dstrspxformdatalineptr" points="243 184, 250 190, 257 184" class="rspoff" />
		<ellipse id="dstrspxformdata1" cx="250" cy="160" rx="10" ry="5" class="rspoff" />
		<rect id="dstrspxformdata2" x="240" y="145" width="20" height="15" class="rspoff" />
		<ellipse id="dstrspxformdata3" cx="250" cy="145" rx="10" ry="5" class="rspoff" />
		<line id="dstrspxformdata4" x1="240" y1="146" x2="240" y2="161" class="rspoff" />
		<line id="dstrspxformdata5" x1="260" y1="146" x2="260" y2="161" class="rspoff" />
		<text id="dstrspxformdata6" x="220" y="128" class="rspoff">Response</text>
		<text id="dstrspxformdata7" x="220" y="137" class="rspoff">Transformed</text>
		<rect id="dstrspxform" rx="1" ry="1" x="300" y="130" width="60" height="40" class="dstoff" />
		<text id="dstrspxformtext1" x="330" y="153" class="dstoff">Transformer</text>
		<text id="dstrspxformtext2" x="330" y="158" class="dstoff"></text>
		
		<polyline id="dstrspdesline" points="250 230, 250 260, 170 260, 170 235" class="rspoff" />
		<polygon id="dstrspdeslineptr" points="163 236, 170 230, 177 236" class="rspoff" />
		<ellipse id="dstrspdesdata1" cx="210" cy="270" rx="10" ry="5" class="rspoff" />
		<rect id="dstrspdesdata2" x="200" y="255" width="20" height="15" class="rspoff" />
		<ellipse id="dstrspdesdata3" cx="210" cy="255" rx="10" ry="5" class="rspoff" />
		<line id="dstrspdesdata4" x1="200" y1="256" x2="200" y2="271" class="rspoff" />
		<line id="dstrspdesdata5" x1="220" y1="256" x2="220" y2="271" class="rspoff" />
		<text id="dstrspdesdata6" x="180" y="285" class="rspoff">Processed Response</text>
		<rect id="dstrspdes" rx="1" ry="1" x="220" y="190" width="60" height="40" class="dstoff" />
		<text id="dstrspdestext1" x="250" y="208" class="dstoff">Deserialized</text>
		<text id="dstrspdestext2" x="250" y="218" class="dstoff">e.g., to HL7</text>
		
		<line id="dstrspwaitline" x1="140" y1="210" x2="120" y2="210" class="dstoff" />
		<polygon id="dstrspwaitptr" points="126 217, 120 210, 126 203" class="dstoff" />
		<rect id="dstrspwait" rx="1" ry="1" x="140" y="190" width="60" height="40" class="dstoff" />
		<text id="dstrspwaittext1" x="170" y="208" class="dstoff">Wait</text>
		<text id="dstrspwaittext2" x="170" y="218" class="dstoff">for All</text>
     </xsl:template>

	<xsl:template match="text()" mode="xformdstrspall" />

</xsl:stylesheet>




____________________________________________________________
Step 20 Postprocessor		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcpost" />
     </xsl:template>

	<xsl:template match="/channel/postprocessingScript" mode="srcpost">
		<xsl:variable name="textvalue"><xsl:value-of select="normalize-space()" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="(string-length($textvalue) > 0) and not ($textvalue = '// This script executes once after a message has been processed // Responses returned from here will be stored as &quot;Postprocessor&quot; in the response map return;')">srcon</xsl:when>
				<xsl:otherwise>srcoff</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<line id="srcpostline" x1="90" y1="165" x2="90" y2="190" class="{$style}" />
		<polygon id="srcpostlineptr" points="83 166, 90 160, 97 166" class="{$style}" />
		<rect id="srcpost" rx="1" ry="1" x="60" y="190" width="60" height="40" class="{$style}" />
		<text id="srcposttext1" x="90" y="208" class="{$style}">Postprocessor</text>
		<text id="srcpostext2" x="90" y="218" class="{$style}">Script</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcpost" />

</xsl:stylesheet>


____________________________________________________________
Step 21 Select Response		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- @apiinfo """Highlights not yet added, not yet needed.""" -->

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcselrsp" />
     </xsl:template>

	<xsl:template match="/channel/sourceConnector" mode="srcselrsp">
		<!-- TODO: (when needed) research XSL to toggle srcoff/srcon -->
		<xsl:variable name="style">srcoff</xsl:variable>
		<line id="srcselrspline" x1="90" y1="95" x2="90" y2="120" class="{$style}" />
		<polygon id="srcselrsplineptr" points="83 96, 90 90, 97 96" class="{$style}" />
		<rect id="srcselrsp" rx="1" ry="1" x="60" y="120" width="60" height="40" class="{$style}" />
		<text id="srcselrsptext1" x="90" y="138" class="{$style}">Select</text>
		<text id="srcselrsptext2" x="90" y="148" class="{$style}">Response</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcselrsp" />

</xsl:stylesheet>



____________________________________________________________
Step 22 Source Response		

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="srcrsp" />
     </xsl:template>

	<xsl:template match="/channel/sourceConnector/properties" mode="srcrsp">
		<xsl:variable name="type"><xsl:value-of select="@class" /></xsl:variable>
		<xsl:variable name="srcrsp"><xsl:value-of select="sourceConnectorProperties/responseVariable/text()" /></xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="not (($srcrsp = 'None') or (contains($type, 'JavaScriptReceiver')))">rspon</xsl:when>
				<xsl:otherwise>rspoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<polyline id="srcrspline" points="90 50, 90 35, 20 35, 20 45" class="{$style}" />
		<polygon id="srcrsplineptr" points="13 44, 20 50, 27 44" class="{$style}" />
		<text id="srcrsptext" x="37" y="29" class="{$style}">Response</text>
     </xsl:template>

	<xsl:template match="text()" mode="srcrsp" />

</xsl:stylesheet>


____________________________________________________________
Step 23 Destination Transformer		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="dstxform" />
     </xsl:template>

  	<xsl:template match="/channel/destinationConnectors" mode="dstxform">
		<xsl:variable name="stepcount">
  			<xsl:value-of select="count(connector/transformer/elements[count(*) > 0])" />
  		</xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="($stepcount > 0)">dston</xsl:when>
				<xsl:otherwise>dstoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<line id="dstxformline" x1="490" y1="170" x2="490" y2="185" class="{$style}" />
		<polygon id="dstxformlineptr" points="483 184, 490 190, 497 184" class="{$style}" />
		<rect id="dstxform" rx="1" ry="1" x="460" y="130" width="60" height="40" class="{$style}" />
		<text id="dstxformtext1" x="490" y="153" class="{$style}">Transformer</text>
		<text id="dstxformtext2" x="490" y="158" class="{$style}"></text>
	</xsl:template>

	<xsl:template match="text()" mode="dstxform" />

</xsl:stylesheet>




____________________________________________________________
Step 24 Destination Filter		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="dstfil" />
     </xsl:template>

  	<xsl:template match="/channel/destinationConnectors" mode="dstfil">
		<xsl:variable name="stepcount">
  			<xsl:value-of select="count(connector/filter/elements[count(*) > 0])" />
  		</xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="($stepcount > 0)">dston</xsl:when>
				<xsl:otherwise>dstoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>
		<line id="dstfilline" x1="542" y1="192" x2="525" y2="175" class="{$style}" />
		<polygon id="dstfillineptr" points="520 180, 520 170, 530 170" class="{$style}" />
		<rect id="dstfil" rx="1" ry="1" x="540" y="190" width="60" height="40" class="{$style}" />
		<text id="dstfiltext1" x="570" y="213" class="{$style}">Filter</text>
		<text id="dstfiltext2" x="570" y="218" class="{$style}"></text>
	</xsl:template>

	<xsl:template match="text()" mode="dstfil" />

</xsl:stylesheet>



____________________________________________________________
Step 25 Destination Connector		

<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

     <xsl:template match="/" >
     	<xsl:apply-templates select="*|text()" mode="dstconn" />
     </xsl:template>

  	<xsl:template match="/channel/destinationConnectors" mode="dstconn">
		<xsl:variable name="stepcount">
  			<xsl:value-of select="count(connector)" />
  		</xsl:variable>
		<xsl:variable name="style">
			<xsl:choose>
				<xsl:when test="($stepcount > 0)">dston</xsl:when>
				<xsl:otherwise>dstoff</xsl:otherwise>
			</xsl:choose>	
		</xsl:variable>		
		<line id="dstconnline" x1="430" y1="175" x2="430" y2="190" class="{$style}" />
		<polygon id="dstconnlineptr" points="423 176, 430 170, 437 176" class="{$style}" />
		<rect id="dstconn" rx="1" ry="1" x="380" y="190" width="60" height="40" class="{$style}" />
		<text id="dstconntext1" x="410" y="208" class="{$style}">Destination</text>
		<text id="dstconntext2" x="410" y="218" class="{$style}">Connector</text>
		
		<line id="dstsline2" x1="390" y1="170" x2="390" y2="185" class="{$style}" />
		<polygon id="dstslineptr2" points="383 184, 390 190, 397 184" class="{$style}" />
		<rect id="dsts" rx="1" ry="1" x="380" y="130" width="60" height="40" class="{$style}" />
		<text id="dststext1" x="410" y="153" class="{$style}">Destination(s)</text>
		<text id="dststext2" x="410" y="158" class="{$style}"></text>
	</xsl:template>
	
	<xsl:template match="text()" mode="dstconn" />

</xsl:stylesheet>




____________________________________________________________
Connector		


com.mirth.connect.connectors.file.FileDispatcherProperties connectors are not exported for code review.



________________________________________________________________________________________________________________________________
Destination 2 Deploy and Other Channel Scripts for Code Review



____________________________________________________________
Filter		



____________________________________________________________
Rule 0 		

return ((channelMap.get('scriptdeploy') != "") || (channelMap.get('scriptundeploy') != "") || (channelMap.get('scriptpre') != "") || (channelMap.get('scriptpost') != ""));



____________________________________________________________
Connector		


com.mirth.connect.connectors.file.FileDispatcherProperties connectors are not exported for code review.



________________________________________________________________________________________________________________________________
Destination 3 Source Connector Script for Code Review



____________________________________________________________
Filter		



____________________________________________________________
Rule 0 		

return (channelMap.get('scriptsrcconn') != "");



____________________________________________________________
Connector		


com.mirth.connect.connectors.file.FileDispatcherProperties connectors are not exported for code review.



________________________________________________________________________________________________________________________________
Destination 4 Source Transformer Scripts for Code Review



____________________________________________________________
Filter		



____________________________________________________________
Rule 0 		

return (channelMap.get('scriptsrcxform') != "");



____________________________________________________________
Connector		


com.mirth.connect.connectors.file.FileDispatcherProperties connectors are not exported for code review.



________________________________________________________________________________________________________________________________
Destination 5 Source Filter Scripts for Code Review



____________________________________________________________
Filter		



____________________________________________________________
Rule 0 		

return (channelMap.get('scriptsrcfil') != "");



____________________________________________________________
Connector		


com.mirth.connect.connectors.file.FileDispatcherProperties connectors are not exported for code review.



________________________________________________________________________________________________________________________________
Destination 6 Destination Scripts for Code Review



____________________________________________________________
Filter		



____________________________________________________________
Rule 0 		

return (channelMap.get('scriptdstconn') != "");



____________________________________________________________
Connector		


com.mirth.connect.connectors.file.FileDispatcherProperties connectors are not exported for code review.
```