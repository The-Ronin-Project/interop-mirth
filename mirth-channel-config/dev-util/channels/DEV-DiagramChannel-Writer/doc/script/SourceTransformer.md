# DEV-DiagramChannel-Writer

__Channel Export__ - Tue Mar 29 2022 17:13:09 GMT-0000 (UTC)

__Source Transformer Scripts__
```


____________________________________________________________
Step 0 Channel Name for Code Review		


var name = String(sourceMap.get('originalFilename'));
var output = name.split('.xml')[0];
channelMap.put('exportchannelname', output);

____________________________________________________________
Step 1 Milliseconds for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	
	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="*|text()" mode="lastmodified" />
	</xsl:template>
	
	<xsl:template match="/channel" mode="lastmodified">
		<xsl:value-of select="exportData/metadata/lastModified/time/text()" />
	</xsl:template>
	
	<xsl:template match="*|text()" mode="lastmodified" />

</xsl:stylesheet>

____________________________________________________________
Step 2 Date for Code Review		


var dateString = String(channelMap.get('exportlastmodified'));
var output = new Date(parseInt(dateString)).toString();
channelMap.put('exportlastmodified', output);

____________________________________________________________
Step 3 Deploy Script for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="channel/deployScript" mode="scriptdeploy" />
	</xsl:template>
	
	<xsl:template match="/channel/deployScript" mode="scriptdeploy">
		<xsl:call-template name="unescape">
			<xsl:with-param name="script" select="text()" />
		</xsl:call-template>
	</xsl:template>	

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
	
	<xsl:template match="*|text()" mode="scriptdeploy" />

</xsl:stylesheet>


____________________________________________________________
Step 4 Undeploy Script for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	
	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="channel/undeployScript" mode="scriptundeploy" />
	</xsl:template>
	
	<xsl:template match="/channel/undeployScript" mode="scriptundeploy">
		<xsl:call-template name="unescape">
			<xsl:with-param name="script" select="text()" />
		</xsl:call-template>
	</xsl:template>

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
		
	<xsl:template match="*|text()" mode="scriptundeploy" />

</xsl:stylesheet>


____________________________________________________________
Step 5 Preprocessor Script for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="channel/preprocessingScript" mode="scriptpre" />
	</xsl:template>
	
	<xsl:template match="/channel/preprocessingScript" mode="scriptpre">
		<xsl:call-template name="unescape">
			<xsl:with-param name="script" select="text()" />
		</xsl:call-template>
	</xsl:template>

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
	
	<xsl:template match="*|text()" mode="scriptpre" />

</xsl:stylesheet>


____________________________________________________________
Step 6 Postprocessor Script for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="channel/postprocessingScript" mode="scriptpost" />
	</xsl:template>
	
	<xsl:template match="/channel/postprocessingScript" mode="scriptpost">
		<xsl:call-template name="unescape">
			<xsl:with-param name="script" select="text()" />
		</xsl:call-template>
	</xsl:template>

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
	
	<xsl:template match="*|text()" mode="scriptpost" />

</xsl:stylesheet>


____________________________________________________________
Step 7 Source Connector Scripts for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	
	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/">
		<xsl:apply-templates select="channel/sourceConnector" mode="scriptsrcconn" />
	</xsl:template>
	
	<xsl:template match="/channel/sourceConnector" mode="scriptsrcconn">
		<xsl:variable name="type"><xsl:value-of select="properties/@class" /></xsl:variable>
		<xsl:choose>
			<xsl:when test="(contains($type, 'JavaScriptReceiver'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="properties/script/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="(contains($type, 'DatabaseReceiver'))">				
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="properties/select/text()" />
				</xsl:call-template>
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="properties/update/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise></xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
		
	<xsl:template match="*|text()" mode="scriptsrcconn" />

</xsl:stylesheet>


____________________________________________________________
Step 8 Source Filter Scripts for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="channel/sourceConnector/filter/elements" mode="scriptsrcfil" />
	</xsl:template>
	
	<xsl:template match="/channel/sourceConnector/filter/elements" mode="scriptsrcfil" >
		<xsl:apply-templates select="*" mode="scriptsrcfil" />
	</xsl:template>
	
	<xsl:template match="/channel/sourceConnector/filter/elements/*" mode="scriptsrcfil">
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>		
<xsl:text>

____________________________________________________________
Rule </xsl:text><xsl:value-of select="sequenceNumber/text()" /><xsl:text> </xsl:text><xsl:value-of select="name/text()" /><xsl:text>		


</xsl:text>
		<xsl:choose>
			<xsl:when test="(contains($type, 'JavaScript'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="script/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="(contains($type, 'Xslt'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="template/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$type" /><xsl:text> rules are not exported for code review.</xsl:text>
			</xsl:otherwise>
		</xsl:choose>	
	</xsl:template>
	
	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
	
	<xsl:template match="*|text()" mode="scriptsrcfil" />

</xsl:stylesheet>


____________________________________________________________
Step 9 Source Transformer Scripts for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />

	<xsl:template match="/" >
		<xsl:apply-templates select="channel/sourceConnector/transformer/elements" mode="scriptsrcxform" />
	</xsl:template>

	<xsl:template match="/channel/sourceConnector/transformer/elements" mode="scriptsrcxform" >
		<xsl:apply-templates select="*" mode="scriptsrcxform" />
	</xsl:template>
     
	<xsl:template match="/channel/sourceConnector/transformer/elements/*" mode="scriptsrcxform" >
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>	
<xsl:text>

____________________________________________________________
Step </xsl:text><xsl:value-of select="sequenceNumber/text()" /><xsl:text> </xsl:text><xsl:value-of select="name/text()" /><xsl:text>		


</xsl:text>
		<xsl:choose>
			<xsl:when test="(contains($type, 'JavaScript'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="script/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="(contains($type, 'Xslt'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="template/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$type" /><xsl:text> steps are not exported for code review.</xsl:text>
			</xsl:otherwise>
		</xsl:choose>	
     </xsl:template>

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>
	
	<xsl:template match="*|text()" mode="scriptsrcxform" />

</xsl:stylesheet>


____________________________________________________________
Step 10 Destination Scripts for Code Review		


<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />
	
	<xsl:template match="/" >
		<xsl:apply-templates select="/channel/destinationConnectors" mode="scriptdst" />
	</xsl:template>

  	<xsl:template match="/channel/destinationConnectors" mode="scriptdst">
		<xsl:variable name="dstcount"><xsl:value-of select="count(connector)" /></xsl:variable>
		<xsl:choose>
			<xsl:when test="$dstcount = 1">
				<xsl:text>This channel has </xsl:text><xsl:value-of select="$dstcount" /><xsl:text> destination.</xsl:text>
	 			<xsl:apply-templates select="connector" mode="scriptdst" />
	 		</xsl:when>
			<xsl:when test="$dstcount > 1">
				<xsl:text>This channel has </xsl:text><xsl:value-of select="$dstcount" /><xsl:text> destinations.</xsl:text>
	 			<xsl:apply-templates select="connector" mode="scriptdst" />
	 		</xsl:when>
	 		<xsl:otherwise></xsl:otherwise>
	 	</xsl:choose>
   	</xsl:template>

    	<xsl:template match="/channel/destinationConnectors/connector" mode="scriptdst">
<xsl:text>



________________________________________________________________________________________________________________________________
Destination </xsl:text><xsl:value-of select="position()"/><xsl:text> </xsl:text><xsl:value-of select="name/text()" /><xsl:text>

</xsl:text>
 		<xsl:apply-templates select="filter/elements" mode="scriptdstfil" />
 		<xsl:apply-templates select="transformer/elements" mode="scriptdstxform" />		
		<xsl:call-template name="getdstconn">
			<xsl:with-param name="connector" select="." />
		</xsl:call-template>		
   	</xsl:template>
 
	<xsl:template match="/channel/destinationConnectors/connector/filter/elements" mode="scriptdstfil" >
		<xsl:variable name="stepcount"><xsl:value-of select="count(*)" /></xsl:variable>
		<xsl:if test="$stepcount > 0">
<xsl:text>

____________________________________________________________
Filter		

</xsl:text>
    			<xsl:apply-templates select="*" mode="scriptdstfil" />
    		</xsl:if>
	</xsl:template>
  
	<xsl:template match="/channel/destinationConnectors/connector/filter/elements/*" mode="scriptdstfil">
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>		
<xsl:text>

____________________________________________________________
Rule </xsl:text><xsl:value-of select="sequenceNumber/text()" /><xsl:text> </xsl:text><xsl:value-of select="name/text()" /><xsl:text>		

</xsl:text>
		<xsl:choose>
			<xsl:when test="(contains($type, 'JavaScript'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="script/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="(contains($type, 'Xslt'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="template/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$type" /><xsl:text> rules are not exported for code review.</xsl:text>
			</xsl:otherwise>
		</xsl:choose>	
     </xsl:template>

	<xsl:template match="/channel/destinationConnectors/connector/transformer/elements" mode="scriptdstxform" >
		<xsl:variable name="stepcount"><xsl:value-of select="count(*)" /></xsl:variable>
		<xsl:if test="$stepcount > 0">
<xsl:text>

____________________________________________________________
Transformer

</xsl:text>
    			<xsl:apply-templates select="*" mode="scriptdstxform" />
    		</xsl:if>
	</xsl:template>
  
	<xsl:template match="/channel/destinationConnectors/connector/transformer/elements/*" mode="scriptdstxform">
		<xsl:variable name="type"><xsl:value-of select="local-name()" /></xsl:variable>		
<xsl:text>

____________________________________________________________
Step </xsl:text><xsl:value-of select="sequenceNumber/text()" /><xsl:text> </xsl:text><xsl:value-of select="name/text()" /><xsl:text>		

</xsl:text>
		<xsl:choose>
			<xsl:when test="(contains($type, 'JavaScript'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="script/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="(contains($type, 'Xslt'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="template/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$type" /><xsl:text> steps are not exported for code review.</xsl:text>
			</xsl:otherwise>
		</xsl:choose>	
     </xsl:template>

	<xsl:template name="getdstconn">
		<xsl:param name="connector" />
<xsl:text>



____________________________________________________________
Connector		


</xsl:text>
		<xsl:variable name="type"><xsl:value-of select="$connector/properties/@class" /></xsl:variable>
		<xsl:choose>
			<xsl:when test="(contains($type, 'JavaScript'))">
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="$connector/properties/script/text()" />
				</xsl:call-template>
			</xsl:when>				
			<xsl:when test="(contains($type, 'Database'))">				
				<xsl:call-template name="unescape">
					<xsl:with-param name="script" select="$connector/properties/query/text()" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$type" /><xsl:text> connectors are not exported for code review.</xsl:text>
			</xsl:otherwise>
		</xsl:choose>	
	</xsl:template>   

	<xsl:template name="unescape">
		<xsl:output method="text" />
		<xsl:param name="script" />
		<xsl:value-of select="$script" />
	</xsl:template>

	<xsl:template match="*|text()" mode="scriptdst" />
	<xsl:template match="*|text()" mode="scriptdstfil" />
	<xsl:template match="*|text()" mode="scriptdstxform" />
	<xsl:template match="*|text()" mode="scriptdstconn" />	
</xsl:stylesheet>

```